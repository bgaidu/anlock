package com.kidslock.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import com.kidslock.app.question.AdaptiveDifficulty
import java.math.BigInteger
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrefManager(private val context: Context) {

    companion object {
        const val PREF_NAME = "kids_lock_prefs"
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
        private const val KEY_QUESTION_JUDGMENT = "question_judgment"
        private const val KEY_QUESTION_FILL = "question_fill"
        private const val KEY_REVIEW_BOOST = "review_boost"
        private const val KEY_ADAPTIVE_DIFFICULTY = "adaptive_difficulty"
        private const val KEY_ADAPTIVE_GRADE = "adaptive_grade"
        private const val KEY_RECENT_ANSWERS = "recent_answers"
        private const val RECENT_ANSWERS_WINDOW = 20
        private const val KEY_WEEKLY_REPORT = "weekly_report_enabled"
        private const val SUBJECT_SEP = ","
        private val VALID_SUBJECTS = setOf("pinyin", "math", "english", "ciyu")
        private val SUBJECT_ORDER = arrayOf("pinyin", "math", "english", "ciyu")
        private const val KEY_STAT_CORRECT = "stat_correct"
        private const val KEY_STAT_TOTAL = "stat_total"
        private const val KEY_STARS = "stars"
        private const val KEY_STAT_BEST_COMBO = "stat_best_combo"
        private const val KEY_LEGACY_MIGRATED = "legacy_defaults_migrated"
        private const val KEY_EMERGENCY_REBOOT_UNLOCK = "emergency_reboot_unlock"
        private const val KEY_SHOW_COUNTDOWN = "show_countdown"
        private const val DEFAULT_SHOW_COUNTDOWN = true
        private const val KEY_SETTINGS_ALLOW_UNTIL = "settings_allow_until"
        private const val KEY_DAILY_LIMIT_MIN = "daily_limit_min"
        // 周末（周六/周日）单独额度；-1 = 跟随平日额度（默认，保持旧行为）
        private const val KEY_DAILY_LIMIT_WEEKEND_MIN = "daily_limit_weekend_min"
        private const val KEY_DAILY_USED_MS = "daily_used_ms"
        private const val KEY_DAILY_DATE = "daily_date"
        private const val KEY_DAILY_BYPASS_UNTIL = "daily_bypass_until"
        private const val KEY_USAGE_PREFIX = "usage_"
        private const val DEFAULT_DAILY_LIMIT_MIN = 0 // 0 = 不限制，保持旧行为
        private const val KEY_LAST_SEEN_DAY = "last_seen_day"
        private const val KEY_LAST_SEEN_WALL = "last_seen_wall"
        private const val KEY_LAST_SEEN_ELAPSED = "last_seen_elapsed"
        private const val KEY_CLOCK_ROLLBACK = "clock_rollback"
        // 使用时段：按周一~周日（下标 0=周一）存起止分钟数，-1 表示该天不限
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START_PREFIX = "schedule_start_"
        private const val KEY_SCHEDULE_END_PREFIX = "schedule_end_"
        private const val KEY_SCHEDULE_BYPASS_UNTIL = "schedule_bypass_until"
        private const val DEFAULT_SCHEDULE_START_MIN = 8 * 60
        private const val DEFAULT_SCHEDULE_END_MIN = 21 * 60
        private const val KEY_LOCKED_BY_SCHEDULE = "locked_by_schedule"
        private const val KEY_LOCK_REASON = "lock_reason"
        private const val KEY_SCHEDULE_FREE_MODE = "schedule_free_mode"
        const val REASON_SCHEDULE = "schedule"
        const val REASON_TIME = "time"
        const val REASON_MANUAL = "manual"
        // 星星兑换：家长可自定义「多少颗星换多少分钟」
        private const val KEY_STAR_EXCHANGE_STARS = "star_exchange_stars"
        private const val KEY_STAR_EXCHANGE_MINUTES = "star_exchange_minutes"
        private const val KEY_BONUS_PENDING_MIN = "bonus_pending_min"
        private const val KEY_DAILY_BONUS_MIN = "daily_bonus_min"
        // 局域网家长面板
        private const val KEY_LAN_ENABLED = "lan_enabled"
        private const val KEY_LAN_TOKEN = "lan_token"
        private const val LAN_TOKEN_LENGTH = 10
        private const val LAN_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val DEFAULT_STAR_EXCHANGE_STARS = 10
        private const val DEFAULT_STAR_EXCHANGE_MINUTES = 10
    }

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 系统时间篡改检测用（内存里维护最近一次观察值，最多每分钟落盘一次）
    private var lastWallCheck = 0L
    private var lastElapsedCheck = 0L
    private var lastStampPersist = 0L

    init {
        migrateLegacyTimer()
        migrateQuestionDefaults()
        migrateLegacyDefaults()
        lastWallCheck = prefs.getLong(KEY_LAST_SEEN_WALL, 0)
        lastElapsedCheck = prefs.getLong(KEY_LAST_SEEN_ELAPSED, 0)
        lastStampPersist = lastElapsedCheck
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

    // 屏幕角标：计时中在右上角显示剩余时间（需要悬浮窗权限），默认开启
    fun isShowCountdown(): Boolean = prefs.getBoolean(KEY_SHOW_COUNTDOWN, DEFAULT_SHOW_COUNTDOWN)

    fun setShowCountdown(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_COUNTDOWN, enabled).apply()
    }

    /**
     * 管控期间拦截系统设置，家长通过 PIN 后临时放行。
     * 用 elapsedRealtime（开机单调时钟）而不是墙钟，改系统时间无法延长放行窗口。
     */
    fun allowSettingsTemporarily(durationMs: Long = 2 * 60 * 1000L) {
        prefs.edit()
            .putLong(KEY_SETTINGS_ALLOW_UNTIL, SystemClock.elapsedRealtime() + durationMs)
            .apply()
    }

    fun isSettingsAllowedNow(): Boolean {
        val until = prefs.getLong(KEY_SETTINGS_ALLOW_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
    }

    // ===== 每日总时长上限 =====
    // 0 表示不限制（默认，避免升级后静默改变老用户行为）。
    fun getDailyLimitMinutes(now: Calendar = Calendar.getInstance()): Int {
        val weekday = prefs.getInt(KEY_DAILY_LIMIT_MIN, DEFAULT_DAILY_LIMIT_MIN)
        if (!isWeekend(now)) return weekday
        val weekend = prefs.getInt(KEY_DAILY_LIMIT_WEEKEND_MIN, -1)
        return if (weekend < 0) weekday else weekend
    }

    /** 周末额度原值：-1 表示跟随平日。 */
    fun getWeekendDailyLimitMinutes(): Int = prefs.getInt(KEY_DAILY_LIMIT_WEEKEND_MIN, -1)

    /** 平日额度原值。 */
    fun getWeekdayDailyLimitMinutes(): Int = prefs.getInt(KEY_DAILY_LIMIT_MIN, DEFAULT_DAILY_LIMIT_MIN)

    fun setDailyLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT_MIN, minutes.coerceIn(0, 12 * 60)).apply()
    }

    /** 设置周末额度；传 -1 表示跟随平日额度。 */
    fun setWeekendDailyLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT_WEEKEND_MIN, minutes.coerceIn(-1, 12 * 60)).apply()
    }

    fun isWeekend(now: Calendar = Calendar.getInstance()): Boolean {
        val dow = now.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    fun getDailyUsedMs(): Long {
        rolloverDailyIfNeeded()
        return prefs.getLong(KEY_DAILY_USED_MS, 0)
    }

    fun addDailyUsedMs(millis: Long) {
        if (millis <= 0) return
        rolloverDailyIfNeeded()
        val used = prefs.getLong(KEY_DAILY_USED_MS, 0) + millis
        prefs.edit()
            .putLong(KEY_DAILY_USED_MS, used)
            // 同步一份按天的历史记录，供「近 7 天使用记录」展示
            .putLong(KEY_USAGE_PREFIX + todayKey(), used)
            .apply()
    }

    /** 近 N 天使用记录，按日期倒序：("2026-11-11", 毫秒)。 */
    fun getUsageHistory(days: Int = 7): List<Pair<String, Long>> {
        rolloverDailyIfNeeded()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        val out = ArrayList<Pair<String, Long>>(days)
        for (i in 0 until days) {
            val key = format.format(calendar.time)
            out.add(key to prefs.getLong(KEY_USAGE_PREFIX + key, 0))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out
    }

    /** 今天还能用多少毫秒；未开启上限时返回 Long.MAX_VALUE。 */
    fun getDailyRemainingMs(): Long {
        if (isDailyBypassActive()) return Long.MAX_VALUE
        val limit = getDailyLimitMinutes()
        if (limit <= 0) return Long.MAX_VALUE
        // 星星兑换/家长奖励的时长会把今天的总上限一起抬高
        val allowanceMin = limit + getDailyBonusMinutes() + getBonusPendingMinutes()
        return (allowanceMin * 60_000L - getDailyUsedMs()).coerceAtLeast(0)
    }

    // ===== 星星兑换时长 =====
    fun getStarExchangeStars(): Int =
        prefs.getInt(KEY_STAR_EXCHANGE_STARS, DEFAULT_STAR_EXCHANGE_STARS).coerceAtLeast(1)

    fun getStarExchangeMinutes(): Int =
        prefs.getInt(KEY_STAR_EXCHANGE_MINUTES, DEFAULT_STAR_EXCHANGE_MINUTES).coerceAtLeast(1)

    fun setStarExchange(stars: Int, minutes: Int) {
        prefs.edit()
            .putInt(KEY_STAR_EXCHANGE_STARS, stars.coerceIn(1, 999))
            .putInt(KEY_STAR_EXCHANGE_MINUTES, minutes.coerceIn(1, 180))
            .apply()
    }

    /**
     * 兑换成功：扣星星，奖励时长进入「待生效」池，下次解锁时并入本次时长。
     * 同时把奖励计入今日额度，避免开了每日上限时被立刻夹回去。
     */
    fun redeemStars(): Boolean {
        val need = getStarExchangeStars()
        val stars = getStars()
        if (stars < need) return false
        val minutes = getStarExchangeMinutes()
        prefs.edit()
            .putInt(KEY_STARS, stars - need)
            .putInt(KEY_BONUS_PENDING_MIN, getBonusPendingMinutes() + minutes)
            .apply()
        return true
    }

    fun getBonusPendingMinutes(): Int = prefs.getInt(KEY_BONUS_PENDING_MIN, 0)

    /** 解锁时调用：把「待生效」奖励转成今天的额度。 */
    fun consumeBonusMinutes(): Int {
        val pending = getBonusPendingMinutes()
        if (pending <= 0) return 0
        rolloverDailyIfNeeded()
        prefs.edit()
            .putInt(KEY_BONUS_PENDING_MIN, 0)
            .putInt(KEY_DAILY_BONUS_MIN, getDailyBonusMinutes() + pending)
            .apply()
        return pending
    }

    fun getDailyBonusMinutes(): Int = prefs.getInt(KEY_DAILY_BONUS_MIN, 0)

    /**
     * 家长 PIN 放行：临时不受每日上限约束（一次性，过点自动失效）。
     * 用于家长自己要继续使用、或孩子今天已经用完但确有需要的情况。
     */
    fun grantDailyBypass(minutes: Int) {
        if (minutes <= 0) return
        prefs.edit()
            .putLong(KEY_DAILY_BYPASS_UNTIL, SystemClock.elapsedRealtime() + minutes * 60_000L)
            .apply()
    }

    fun isDailyBypassActive(): Boolean {
        val until = prefs.getLong(KEY_DAILY_BYPASS_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
    }

    // ===== 使用时段（周一~周日） =====
    // 每天允许使用的起止时间，-1 表示该天不限制；跨零点（如 20:00–07:00）也支持。
    fun isScheduleEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)

    /**
     * 仅时段限制：时段内不自动计时、不要求答题（自由使用），时段外照常锁屏。
     * false = 时段内仍需答题/计时管控（默认，保持原有行为）。
     */
    fun isScheduleFreeMode(): Boolean = prefs.getBoolean(KEY_SCHEDULE_FREE_MODE, false)

    fun setScheduleFreeMode(free: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULE_FREE_MODE, free).apply()
    }

    // ===== 局域网家长面板 =====
    fun isLanEnabled(): Boolean = prefs.getBoolean(KEY_LAN_ENABLED, false)

    fun setLanEnabled(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_LAN_ENABLED, enabled)
        if (enabled && prefs.getString(KEY_LAN_TOKEN, null).isNullOrEmpty()) {
            editor.putString(KEY_LAN_TOKEN, generateLanToken())
        }
        editor.apply()
    }

    /** 访问口令：开启面板时自动生成，家长可重新生成让旧链接立即失效。 */
    fun getLanToken(): String {
        val existing = prefs.getString(KEY_LAN_TOKEN, null)
        if (!existing.isNullOrEmpty()) return existing
        val token = generateLanToken()
        prefs.edit().putString(KEY_LAN_TOKEN, token).apply()
        return token
    }

    fun regenerateLanToken(): String {
        val token = generateLanToken()
        prefs.edit().putString(KEY_LAN_TOKEN, token).apply()
        return token
    }

    private fun generateLanToken(): String {
        val random = SecureRandom()
        val sb = StringBuilder(LAN_TOKEN_LENGTH)
        repeat(LAN_TOKEN_LENGTH) {
            sb.append(LAN_TOKEN_ALPHABET[random.nextInt(LAN_TOKEN_ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * 家长加时：计时中直接延长本轮剩余时长并抬高今日额度；
     * 未计时（已锁定）则进入「待生效」池，下次解锁时并入。
     */
    fun addBonusMinutes(minutes: Int) {
        if (minutes <= 0) return
        rolloverDailyIfNeeded()
        if (isTimerActive()) {
            prefs.edit()
                .putInt(KEY_DAILY_BONUS_MIN, getDailyBonusMinutes() + minutes)
                .apply()
            persistTimer(getRemainingMillis() + minutes * 60_000L)
        } else {
            prefs.edit()
                .putInt(KEY_BONUS_PENDING_MIN, getBonusPendingMinutes() + minutes)
                .apply()
        }
    }

    fun setScheduleEnabled(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled)
        if (enabled && !hasAnyScheduleWindow()) {
            // 第一次开启时给一个合理的默认时段，避免「开了但等于没开」
            for (day in 0..6) {
                editor.putInt(KEY_SCHEDULE_START_PREFIX + day, DEFAULT_SCHEDULE_START_MIN)
                editor.putInt(KEY_SCHEDULE_END_PREFIX + day, DEFAULT_SCHEDULE_END_MIN)
            }
        }
        editor.apply()
    }

    fun getScheduleStart(day: Int): Int {
        if (day !in 0..6) return -1
        return prefs.getInt(KEY_SCHEDULE_START_PREFIX + day, -1)
    }

    fun getScheduleEnd(day: Int): Int {
        if (day !in 0..6) return -1
        return prefs.getInt(KEY_SCHEDULE_END_PREFIX + day, -1)
    }

    /** start/end 以「当天 0 点起的分钟数」表示；传 -1 或非法值表示关闭该天限制。 */
    fun setScheduleWindow(day: Int, startMin: Int, endMin: Int) {
        if (day !in 0..6) return
        val valid = startMin in 0..1439 && endMin in 0..1439 && startMin != endMin
        prefs.edit().apply {
            if (valid) {
                putInt(KEY_SCHEDULE_START_PREFIX + day, startMin)
                putInt(KEY_SCHEDULE_END_PREFIX + day, endMin)
            } else {
                remove(KEY_SCHEDULE_START_PREFIX + day)
                remove(KEY_SCHEDULE_END_PREFIX + day)
            }
        }.apply()
    }

    fun clearScheduleDay(day: Int) {
        setScheduleWindow(day, -1, -1)
    }

    /** 0=周一 … 6=周日 */
    fun scheduleDayIndex(now: Calendar = Calendar.getInstance()): Int =
        ScheduleMath.dayIndex(now)

    /** 该天时段的中文描述，例如 "08:00–21:00" / "不限"。 */
    fun scheduleWindowLabel(day: Int): String {
        val start = getScheduleStart(day)
        val end = getScheduleEnd(day)
        if (start < 0 || end < 0) return "不限"
        return ScheduleMath.format(start) + "–" + ScheduleMath.format(end)
    }

    /** 现在是否处于允许使用的时段内（未开启或当天不限时恒为 true）。 */
    fun isWithinSchedule(now: Calendar = Calendar.getInstance()): Boolean {
        if (!isScheduleEnabled()) return true
        if (isScheduleBypassActive()) return true
        val day = scheduleDayIndex(now)
        val start = getScheduleStart(day)
        val end = getScheduleEnd(day)
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return ScheduleMath.isWithin(start, end, minutes)
    }

    /** 家长 PIN 放行：临时跳过使用时段限制。 */
    fun grantScheduleBypass(durationMs: Long = 30 * 60 * 1000L) {
        prefs.edit()
            .putLong(KEY_SCHEDULE_BYPASS_UNTIL, SystemClock.elapsedRealtime() + durationMs)
            .apply()
    }

    fun isScheduleBypassActive(): Boolean {
        val until = prefs.getLong(KEY_SCHEDULE_BYPASS_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
    }

    private fun hasAnyScheduleWindow(): Boolean {
        for (day in 0..6) {
            if (getScheduleStart(day) >= 0 && getScheduleEnd(day) >= 0) return true
        }
        return false
    }

    /** 本次解锁实际可用分钟数：同时受单次观看时长、今日剩余额度与星星奖励约束。 */
    fun effectiveUnlockMinutes(): Int {
        val bonus = getBonusPendingMinutes()
        val dailyRemaining = getDailyRemainingMs()
        if (dailyRemaining <= 0) return 0
        val base = getWatchLimitMinutes() + bonus
        if (dailyRemaining == Long.MAX_VALUE) return base
        // 向上取整到分钟，精确到秒的余量由 TimerManager 每秒扣减时再夹紧
        val minutes = ((dailyRemaining + 59_999) / 60_000).toInt()
        return minOf(base, minutes).coerceAtLeast(1)
    }

    private fun rolloverDailyIfNeeded() {
        val today = todayKey()
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val lastDay = prefs.getString(KEY_LAST_SEEN_DAY, "") ?: ""

        // 协议外改时间：墙钟比单调时钟走得更快（超过 2 小时，且本次间隔 < 10 分钟），
        // 或日期直接回退，都视为篡改，保留当日已用额度不重置。
        var suspicious = false
        if (lastWallCheck > 0 && lastElapsedCheck > 0 && elapsed >= lastElapsedCheck) {
            val wallDelta = wall - lastWallCheck
            val elapsedDelta = elapsed - lastElapsedCheck
            if (wallDelta - elapsedDelta > 2 * 3600_000L && elapsedDelta < 10 * 60_000L) {
                suspicious = true
            }
        }
        lastWallCheck = wall
        lastElapsedCheck = elapsed

        val rolledBack = lastDay.isNotEmpty() && today < lastDay
        if (rolledBack || suspicious) {
            if (!prefs.getBoolean(KEY_CLOCK_ROLLBACK, false)) {
                prefs.edit().putBoolean(KEY_CLOCK_ROLLBACK, true).apply()
                Diag.w("Clock", "time tampering detected: lastDay=$lastDay today=$today")
            }
            return
        }

        if (prefs.getString(KEY_DAILY_DATE, "") != today) {
            prefs.edit()
                .putString(KEY_DAILY_DATE, today)
                .putString(KEY_LAST_SEEN_DAY, today)
                .putLong(KEY_DAILY_USED_MS, 0)
                .putInt(KEY_DAILY_BONUS_MIN, 0)
                .apply()
            lastStampPersist = elapsed
            prefs.edit()
                .putLong(KEY_LAST_SEEN_WALL, wall)
                .putLong(KEY_LAST_SEEN_ELAPSED, elapsed)
                .apply()
        } else if (lastDay != today || elapsed - lastStampPersist > 60_000) {
            lastStampPersist = elapsed
            prefs.edit()
                .putString(KEY_LAST_SEEN_DAY, today)
                .putLong(KEY_LAST_SEEN_WALL, wall)
                .putLong(KEY_LAST_SEEN_ELAPSED, elapsed)
                .apply()
        }
    }

    /** 是否检测到系统时间被改回（家长确认后调用 clearClockRollback）。 */
    fun wasClockRolledBack(): Boolean = prefs.getBoolean(KEY_CLOCK_ROLLBACK, false)

    fun clearClockRollback() {
        prefs.edit().remove(KEY_CLOCK_ROLLBACK).apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getWatchLimitMinutes(): Int = prefs.getInt(KEY_WATCH_LIMIT_MIN, DEFAULT_WATCH_LIMIT_MIN)

    fun setWatchLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_WATCH_LIMIT_MIN, minutes).apply()
    }

    fun isLocked(): Boolean = prefs.getBoolean(KEY_IS_LOCKED, false)

    fun setLocked(locked: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_IS_LOCKED, locked)
        // 解锁时顺手清掉锁屏原因，避免下次误判为「时段锁」自动放行
        if (!locked) {
            editor.remove(KEY_LOCKED_BY_SCHEDULE)
            editor.remove(KEY_LOCK_REASON)
        }
        editor.apply()
    }

    /** 锁定原因：schedule / time / manual（空表示未知，按 time 处理）。 */
    fun setLockReason(reason: String) {
        prefs.edit()
            .putString(KEY_LOCK_REASON, reason)
            .putBoolean(KEY_LOCKED_BY_SCHEDULE, reason == REASON_SCHEDULE)
            .apply()
    }

    fun getLockReason(): String = prefs.getString(KEY_LOCK_REASON, REASON_TIME) ?: REASON_TIME
    fun isLockedBySchedule(): Boolean = prefs.getBoolean(KEY_LOCKED_BY_SCHEDULE, false)

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
            if (unbilled > 0) {
                remaining = maxOf(0L, remaining - unbilled)
                addDailyUsedMs(minOf(unbilled, getRemainingMillis()))
            }
            persistTimer(remaining)
            return
        }
        val awake = SystemClock.uptimeMillis() - prefs.getLong(KEY_TIMER_STAMP_UPTIME, 0)
        if (awake <= 0) return
        val remaining = getRemainingMillis() - awake
        addDailyUsedMs(minOf(awake, getRemainingMillis()))
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
            if (stored == PinHasher.sha256Hex(getOrCreateSalt() + "|" + pin)) {
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
            return hashHex == PinHasher.pbkdf2Hex(pin, PinHasher.hexToBytes(saltHex))
        }
        return false
    }

    fun setParentPin(pin: String?): Boolean {
        if (pin.isNullOrEmpty()) return false
        val salt = PinHasher.newSalt()
        val saltHex = PinHasher.bytesToHex(salt)
        val hashHex = PinHasher.pbkdf2Hex(pin, salt)
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

    /** 判断题：在题库类科目（拼音/英语/语文）里穿插「这个读音/释义对吗」。 */
    fun isJudgmentEnabled(): Boolean = prefs.getBoolean(KEY_QUESTION_JUDGMENT, false)

    fun setJudgmentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUESTION_JUDGMENT, enabled).apply()
    }

    /** 错题加练：每次解锁把到期的错题插到最前面（默认开启）。 */
    fun isReviewBoostEnabled(): Boolean = prefs.getBoolean(KEY_REVIEW_BOOST, true)

    fun setReviewBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REVIEW_BOOST, enabled).apply()
    }

    /** 填空题：数学题改成直接输入答案（默认关闭）。 */
    fun isFillBlankEnabled(): Boolean = prefs.getBoolean(KEY_QUESTION_FILL, false)

    fun setFillBlankEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUESTION_FILL, enabled).apply()
    }

    /** 自适应难度：按最近 20 题正确率自动升/降一个年级（默认关闭）。 */
    fun isAdaptiveDifficultyEnabled(): Boolean = prefs.getBoolean(KEY_ADAPTIVE_DIFFICULTY, false)

    fun setAdaptiveDifficultyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADAPTIVE_DIFFICULTY, enabled).apply()
    }

    /** 自适应后的生效年级；家长改年级后自动夹回「设定年级 ±1」。 */
    fun getAdaptiveGrade(baseGrade: Int): Int {
        val current = prefs.getInt(KEY_ADAPTIVE_GRADE, baseGrade)
        val allowed = AdaptiveDifficulty.range(baseGrade)
        val clamped = current.coerceIn(allowed)
        if (clamped != current) {
            prefs.edit().putInt(KEY_ADAPTIVE_GRADE, clamped).apply()
        }
        return clamped
    }

    fun setAdaptiveGrade(grade: Int) {
        prefs.edit().putInt(KEY_ADAPTIVE_GRADE, grade).apply()
    }

    fun clearAdaptiveGrade() {
        prefs.edit().remove(KEY_ADAPTIVE_GRADE).apply()
    }

    /** 调整过难度后清空最近答题，避免下一次立刻又跳一档。 */
    fun consumeRecentAnswers() {
        prefs.edit().remove(KEY_RECENT_ANSWERS).apply()
    }

    /** 每周日提醒：汇总本周使用时长与答题情况（默认开启）。 */
    fun isWeeklyReportEnabled(): Boolean = prefs.getBoolean(KEY_WEEKLY_REPORT, true)

    fun setWeeklyReportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_REPORT, enabled).apply()
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
        // 最近 N 题用 1/0 串存，供自适应难度判断；只保留最近 20 题，避免无限增长
        val history = prefs.getString(KEY_RECENT_ANSWERS, "") ?: ""
        val updated = (history + if (correct) "1" else "0")
            .takeLast(RECENT_ANSWERS_WINDOW)
        prefs.edit()
            .putInt(KEY_STAT_TOTAL, total)
            .putInt(KEY_STAT_CORRECT, c)
            .putString(KEY_RECENT_ANSWERS, updated)
            .apply()
    }

    /** 最近 N 题的正确率（0-100）；样本不足时返回 -1。 */
    fun getRecentAccuracyPercent(window: Int = RECENT_ANSWERS_WINDOW): Int {
        val history = (prefs.getString(KEY_RECENT_ANSWERS, "") ?: "")
            .takeLast(window.coerceAtLeast(1))
        if (history.length < window) return -1
        val correct = history.count { it == '1' }
        return Math.round(100f * correct / history.length)
    }

    fun getRecentAnsweredCount(): Int =
        (prefs.getString(KEY_RECENT_ANSWERS, "") ?: "").length

    // ===== 星星奖励 / 连对 =====
    // 星星只是正向激励，不直接兑换时长，避免绕过家长设定的额度。
    fun getStars(): Int = prefs.getInt(KEY_STARS, 0)

    fun addStars(count: Int) {
        if (count <= 0) return
        prefs.edit().putInt(KEY_STARS, getStars() + count).apply()
    }

    fun getBestCombo(): Int = prefs.getInt(KEY_STAT_BEST_COMBO, 0)

    fun recordCombo(combo: Int) {
        if (combo > getBestCombo()) {
            prefs.edit().putInt(KEY_STAT_BEST_COMBO, combo).apply()
        }
    }

    fun resetStats() {
        prefs.edit()
            .remove(KEY_STAT_TOTAL)
            .remove(KEY_STAT_CORRECT)
            .remove(KEY_STAT_BEST_COMBO)
            .remove(KEY_RECENT_ANSWERS)
            .apply()
    }

    fun getStatTotal(): Int = prefs.getInt(KEY_STAT_TOTAL, 0)

    fun getStatCorrect(): Int = prefs.getInt(KEY_STAT_CORRECT, 0)

    fun getAccuracyPercent(): Int {
        val total = getStatTotal()
        if (total <= 0) return -1
        return Math.round(100f * getStatCorrect() / total)
    }
}
