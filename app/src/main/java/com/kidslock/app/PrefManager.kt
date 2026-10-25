package com.kidslock.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PrefManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "kids_lock_prefs"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_WATCH_LIMIT_MIN = "watch_limit_min"
        private const val KEY_UNLOCK_COUNT = "unlock_count"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_TIMER_ACTIVE = "timer_active"
        private const val KEY_TIMER_END_TIME = "timer_end_time"
        private const val KEY_TIMER_REMAINING = "timer_remaining_ms"
        private const val KEY_TIMER_STAMP_WALL = "timer_stamp_wall"
        private const val KEY_TIMER_STAMP_UPTIME = "timer_stamp_uptime"
        private const val KEY_TIMER_STAMP_ELAPSED = "timer_stamp_elapsed"
        private const val KEY_PARENT_PIN = "parent_pin"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_FAIL_COUNT = "pin_fail_count"
        private const val KEY_PIN_LOCK_UNTIL = "pin_lock_until"
        private const val KEY_PIN_MODIFIED = "pin_modified"
        private const val PBKDF2_ITERATIONS = 10000
        private const val PIN_HASH_LENGTH = 256
        private const val KEY_QUESTION_GRADE = "question_grade"
        private const val KEY_QUESTION_SUBJECTS = "question_subjects"
        private const val DEFAULT_AUTO_START = true
        private const val DEFAULT_WATCH_LIMIT_MIN = 30
        private const val DEFAULT_UNLOCK_COUNT = 5
        private const val DEFAULT_PARENT_PIN = "1234"
        private const val MAX_PIN_FAILS = 5
        private const val PIN_LOCKOUT_MS = 30_000L
        private const val DEFAULT_QUESTION_GRADE = 3
        private const val DEFAULT_QUESTION_SUBJECTS = "pinyin,math"
        private const val SUBJECT_SEP = ","
        private val VALID_SUBJECTS = setOf("pinyin", "math", "english", "ciyu")
        private val SUBJECT_ORDER = arrayOf("pinyin", "math", "english", "ciyu")
        private const val KEY_STAT_CORRECT = "stat_correct"
        private const val KEY_STAT_TOTAL = "stat_total"
        private const val KEY_LEGACY_MIGRATED = "legacy_defaults_migrated"
        private const val KEY_EMERGENCY_REBOOT_UNLOCK = "emergency_reboot_unlock"
    }

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyTimer()
        migrateQuestionDefaults()
        migrateLegacyDefaults()
    }

    private fun migrateLegacyTimer() {
        if (!prefs.contains(KEY_TIMER_END_TIME)) return
        val end = prefs.getLong(KEY_TIMER_END_TIME, 0)
        val remaining = maxOf(0L, end - System.currentTimeMillis())
        prefs.edit()
            .putLong(KEY_TIMER_REMAINING, remaining)
            .putLong(KEY_TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(KEY_TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_TIMER_STAMP_WALL, System.currentTimeMillis())
            .remove(KEY_TIMER_END_TIME)
            .apply()
    }

    fun isAutoStart(): Boolean = prefs.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START)

    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    // 紧急重启解锁（3 分钟内连续重启 3 次即解锁）：默认关闭——孩子仅凭电源键即可触发，
    // 忘记 PIN 应优先走 adb 紧急文件（见 README「紧急解锁」），由家长自行权衡后开启
    fun isEmergencyRebootUnlock(): Boolean = prefs.getBoolean(KEY_EMERGENCY_REBOOT_UNLOCK, false)

    fun setEmergencyRebootUnlock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMERGENCY_REBOOT_UNLOCK, enabled).apply()
    }

    fun getWatchLimitMinutes(): Int = prefs.getInt(KEY_WATCH_LIMIT_MIN, DEFAULT_WATCH_LIMIT_MIN)

    fun setWatchLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_WATCH_LIMIT_MIN, minutes).apply()
    }

    fun isLocked(): Boolean = prefs.getBoolean(KEY_IS_LOCKED, false)

    fun setLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply()
    }

    fun isTimerActive(): Boolean = prefs.getBoolean(KEY_TIMER_ACTIVE, false)

    fun getRemainingMillis(): Long = prefs.getLong(KEY_TIMER_REMAINING, 0)

    fun startTimer(minutes: Int) {
        prefs.edit()
            .putBoolean(KEY_TIMER_ACTIVE, true)
            .putLong(KEY_TIMER_REMAINING, minutes * 60_000L)
            .putLong(KEY_TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(KEY_TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_TIMER_STAMP_WALL, System.currentTimeMillis())
            .putBoolean(KEY_IS_LOCKED, false)
            .apply()
    }

    fun stopTimer() {
        prefs.edit()
            .putBoolean(KEY_TIMER_ACTIVE, false)
            .putLong(KEY_TIMER_REMAINING, 0)
            .putLong(KEY_TIMER_STAMP_UPTIME, 0)
            .putLong(KEY_TIMER_STAMP_ELAPSED, 0)
            .putLong(KEY_TIMER_STAMP_WALL, 0)
            .remove(KEY_TIMER_END_TIME)
            .apply()
    }

    fun isTimerExpired(): Boolean = isTimerActive() && getRemainingMillis() <= 0

    fun persistTimer(remainingMillis: Long) {
        prefs.edit()
            .putLong(KEY_TIMER_REMAINING, remainingMillis)
            .putLong(KEY_TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(KEY_TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_TIMER_STAMP_WALL, System.currentTimeMillis())
            .apply()
    }

    fun deductOfflineIfAny() {
        if (!isTimerActive()) return
        val lastElapsed = prefs.getLong(KEY_TIMER_STAMP_ELAPSED, 0)
        if (lastElapsed <= 0 || SystemClock.elapsedRealtime() < lastElapsed) {
            val wallStamp = prefs.getLong(KEY_TIMER_STAMP_WALL, 0)
            var remaining = getRemainingMillis()
            val unbilled = if (wallStamp > 0 && remaining > 0) queryForegroundMillisSince(wallStamp) else 0
            if (unbilled > 0) remaining = maxOf(0L, remaining - unbilled)
            persistTimer(remaining)
            return
        }
        val awake = SystemClock.uptimeMillis() - prefs.getLong(KEY_TIMER_STAMP_UPTIME, 0)
        if (awake <= 0) return
        val remaining = getRemainingMillis() - awake
        persistTimer(maxOf(0L, remaining))
    }

    private fun queryForegroundMillisSince(wallStamp: Long): Long {
        val now = System.currentTimeMillis()
        var total = 0L
        try {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
            val events = usm.queryEvents(wallStamp, now) ?: return 0
            val resumedAt = HashMap<String, Long>()
            val ev = UsageEvents.Event()
            while (events.hasNextEvent() && events.getNextEvent(ev)) {
                val pkg = ev.packageName ?: continue
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    resumedAt[pkg] = ev.timeStamp
                } else if (ev.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    val t0 = resumedAt.remove(pkg)
                    if (t0 != null) total += maxOf(0L, ev.timeStamp - maxOf(wallStamp, t0))
                }
            }
            for (t0 in resumedAt.values) {
                total += maxOf(0L, now - maxOf(wallStamp, t0))
            }
        } catch (e: Exception) {
            return 0
        }
        return total
    }

    fun verifyPin(pin: String?): Boolean {
        if (pin.isNullOrEmpty()) return false
        val stored = prefs.getString(KEY_PARENT_PIN, null)
        if (stored == null) return pin == DEFAULT_PARENT_PIN
        if (stored.length == 4 && stored.matches(Regex("\\d{4}"))) {
            if (stored == pin) {
                try {
                    setParentPin(pin)
                    resetPinFailures()
                } catch (e: RuntimeException) {
                    recordPinFailure()
                }
                return true
            }
            return false
        }
        if (stored.length == 64) {
            if (stored == sha256Hex(getOrCreateSalt() + "|" + pin)) {
                try {
                    setParentPin(pin)
                    resetPinFailures()
                } catch (e: RuntimeException) {
                    recordPinFailure()
                }
                return true
            }
            return false
        }
        if (stored.length == 96) {
            val saltHex = stored.substring(0, 32)
            val hashHex = stored.substring(32)
            return hashHex == pbkdf2Hex(pin, hexToBytes(saltHex))
        }
        return false
    }

    fun setParentPin(pin: String?): Boolean {
        if (pin.isNullOrEmpty()) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = bytesToHex(salt)
        val hashHex = pbkdf2Hex(pin, salt)
        if (hashHex.length != 64) {
            throw IllegalStateException("pbkdf2Hex 返回了非 64 位摘要")
        }
        prefs.edit()
            .putString(KEY_PARENT_PIN, saltHex + hashHex)
            .putBoolean(KEY_PIN_MODIFIED, true)
            .apply()
        return true
    }

    fun isPinModified(): Boolean = prefs.getBoolean(KEY_PIN_MODIFIED, false)

    fun shouldForcePinChange(): Boolean {
        if (isPinModified()) return false
        val stored = prefs.getString(KEY_PARENT_PIN, null)
        return stored == null || (stored.length == 4 && stored.matches(Regex("\\d{4}")))
    }

    fun isPinUsable(): Boolean {
        val stored = prefs.getString(KEY_PARENT_PIN, null)
        if (stored == null) return true
        if (stored.length == 4 && stored.matches(Regex("\\d{4}"))) return true
        return stored.length == 64 || stored.length == 96
    }

    private fun getOrCreateSalt(): String {
        var salt: String = prefs.getString(KEY_PIN_SALT, null) ?: BigInteger(64, SecureRandom()).toString(16)
        prefs.edit().putString(KEY_PIN_SALT, salt).apply()
        return salt
    }

    private fun sha256Hex(s: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val d = md.digest(s.toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder(d.size * 2)
            for (b in d) {
                sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                sb.append(Character.forDigit(b.toInt() and 0xF, 16))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 not available", e)
        }
    }

    private fun pbkdf2Hex(password: String, salt: ByteArray): String {
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PIN_HASH_LENGTH)
            val hash = skf.generateSecret(spec).encoded
            return bytesToHex(hash)
        } catch (e: NoSuchAlgorithmException) {
            // 某些设备不支持 PBKDF2WithHmacSHA256，降级到 SHA-256（仍带盐，安全性足够）
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(password.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(md.digest())
        } catch (e: Exception) {
            throw IllegalStateException("PBKDF2 not available", e)
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    fun isPinLocked(): Boolean = System.currentTimeMillis() < prefs.getLong(KEY_PIN_LOCK_UNTIL, 0)

    fun getPinLockRemainingMillis(): Long {
        val until = prefs.getLong(KEY_PIN_LOCK_UNTIL, 0)
        return maxOf(0L, until - System.currentTimeMillis())
    }

    fun recordPinFailure() {
        val fails = prefs.getInt(KEY_PIN_FAIL_COUNT, 0) + 1
        if (fails >= MAX_PIN_FAILS) {
            prefs.edit()
                .putInt(KEY_PIN_FAIL_COUNT, 0)
                .putLong(KEY_PIN_LOCK_UNTIL, System.currentTimeMillis() + PIN_LOCKOUT_MS)
                .apply()
        } else {
            prefs.edit().putInt(KEY_PIN_FAIL_COUNT, fails).apply()
        }
    }

    fun resetPinFailures() {
        prefs.edit().putInt(KEY_PIN_FAIL_COUNT, 0).putLong(KEY_PIN_LOCK_UNTIL, 0).apply()
    }

    // --- 快速重启检测（紧急解锁用） ---
    fun getLastBootTime(): Long = prefs.getLong("last_boot_time", 0)

    fun setLastBootTime(time: Long) {
        prefs.edit().putLong("last_boot_time", time).apply()
    }

    fun getRebootCount(): Int = prefs.getInt("reboot_count", 0)

    fun setRebootCount(count: Int) {
        prefs.edit().putInt("reboot_count", count).apply()
    }

    var grade: Int = DEFAULT_QUESTION_GRADE
        get() {
            val g = prefs.getInt(KEY_QUESTION_GRADE, DEFAULT_QUESTION_GRADE)
            return if (g in 0..6) g else DEFAULT_QUESTION_GRADE
        }
        set(value) {
            if (value < 0 || value > 6) return
            field = value
            prefs.edit().putInt(KEY_QUESTION_GRADE, value).apply()
        }

    var unlockCount: Int = DEFAULT_UNLOCK_COUNT
        get() = prefs.getInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT)
        set(value) {
            field = value
            prefs.edit().putInt(KEY_UNLOCK_COUNT, value).apply()
        }

    var subjects: Set<String>
        get() {
            val out = LinkedHashSet<String>()
            val raw = prefs.getString(KEY_QUESTION_SUBJECTS, null)
            if (!raw.isNullOrEmpty()) {
                for (s in raw.split(SUBJECT_SEP)) {
                    val trimmed = s.trim()
                    if (trimmed.isNotEmpty() && VALID_SUBJECTS.contains(trimmed)) out.add(trimmed)
                }
            }
            return if (out.isEmpty()) LinkedHashSet(setOf("pinyin", "math")) else out
        }
        set(value) {
            prefs.edit().putString(KEY_QUESTION_SUBJECTS, joinSubjects(value)).apply()
        }

    fun isSubjectEnabled(subject: String): Boolean = subjects.contains(subject)

    fun setSubjectEnabled(subject: String, enabled: Boolean): Boolean {
        if (!VALID_SUBJECTS.contains(subject)) return false
        val cur = HashSet(subjects)
        if (enabled) {
            cur.add(subject)
        } else {
            if (cur.size <= 1 && cur.contains(subject)) return false
            cur.remove(subject)
        }
        prefs.edit().putString(KEY_QUESTION_SUBJECTS, joinSubjects(cur)).apply()
        return true
    }

    private fun joinSubjects(set: Set<String>): String {
        val sb = StringBuilder()
        for (s in SUBJECT_ORDER) {
            if (set.contains(s)) {
                if (sb.isNotEmpty()) sb.append(SUBJECT_SEP)
                sb.append(s)
            }
        }
        return sb.toString()
    }

    private fun migrateQuestionDefaults() {
        if (!prefs.contains(KEY_QUESTION_GRADE)) {
            prefs.edit().putInt(KEY_QUESTION_GRADE, DEFAULT_QUESTION_GRADE).apply()
        }
        if (!prefs.contains(KEY_QUESTION_SUBJECTS)) {
            prefs.edit().putString(KEY_QUESTION_SUBJECTS, DEFAULT_QUESTION_SUBJECTS).apply()
        }
    }

    private fun migrateLegacyDefaults() {
        // 一次性迁移旧版存储默认值，必须有标志位：init 在每次构造 PrefManager 时都会执行，
        // 无标志则会把新版用户主动选的「五年级」/「只开拼音」静默重置回新默认
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        if (prefs.contains(KEY_QUESTION_GRADE) && prefs.getInt(KEY_QUESTION_GRADE, DEFAULT_QUESTION_GRADE) == 5) {
            prefs.edit().putInt(KEY_QUESTION_GRADE, DEFAULT_QUESTION_GRADE).apply()
        }
        if (prefs.contains(KEY_QUESTION_SUBJECTS) && prefs.getString(KEY_QUESTION_SUBJECTS, "") == "pinyin") {
            prefs.edit().putString(KEY_QUESTION_SUBJECTS, DEFAULT_QUESTION_SUBJECTS).apply()
        }
        if (prefs.contains(KEY_UNLOCK_COUNT) && prefs.getInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT) == 3) {
            prefs.edit().putInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT).apply()
        }
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    fun setHomeAliasEnabled(context: Context, enabled: Boolean) {
        val alias = ComponentName(context, "com.kidslock.app.LockScreenHome")
        val pm = context.packageManager
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
    }

    fun recordAnswer(correct: Boolean) {
        val total = prefs.getInt(KEY_STAT_TOTAL, 0) + 1
        val c = prefs.getInt(KEY_STAT_CORRECT, 0) + if (correct) 1 else 0
        prefs.edit().putInt(KEY_STAT_TOTAL, total).putInt(KEY_STAT_CORRECT, c).apply()
    }

    fun getStatTotal(): Int = prefs.getInt(KEY_STAT_TOTAL, 0)

    fun getStatCorrect(): Int = prefs.getInt(KEY_STAT_CORRECT, 0)

    fun getAccuracyPercent(): Int {
        val total = getStatTotal()
        if (total <= 0) return -1
        return Math.round(100f * getStatCorrect() / total)
    }
}
