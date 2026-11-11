package com.kidslock.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import com.kidslock.app.prefs.AppLimitSettings
import com.kidslock.app.prefs.AppLimitStore
import com.kidslock.app.prefs.AppOptionSettings
import com.kidslock.app.prefs.AppOptionsStore
import com.kidslock.app.prefs.ClockGuard
import com.kidslock.app.prefs.DailyLimitPolicy
import com.kidslock.app.prefs.PinSettings
import com.kidslock.app.prefs.PinStore
import com.kidslock.app.prefs.PrefKeys
import com.kidslock.app.prefs.QuestionSettings
import com.kidslock.app.prefs.QuestionStore
import com.kidslock.app.prefs.RewardSettings
import com.kidslock.app.prefs.RewardStore
import com.kidslock.app.prefs.ScheduleSettings
import com.kidslock.app.prefs.ScheduleStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 设置与运行时状态的门面。
 *
 * 原来这个类有 1000 行、几十个互不相干的键，改一处要通读全文。现在按职责拆成
 * PIN / 出题 / 时段 / 奖励 / 应用开关五个 Store，由 Kotlin 的 `by` 委托暴露原方法，
 * 调用方（`pref.xxx`）完全不用改。计时对账、每日额度、锁屏状态这些强耦合逻辑留在本类。
 */
class PrefManager private constructor(
    private val appContext: Context,
    private val prefs: SharedPreferences
) : PinSettings by PinStore(prefs),
    QuestionSettings by QuestionStore(prefs),
    ScheduleSettings by ScheduleStore(prefs),
    RewardSettings by RewardStore(prefs),
    AppOptionSettings by AppOptionsStore(prefs),
    AppLimitSettings by AppLimitStore(prefs) {

    companion object {
        const val PREF_NAME = PrefKeys.PREF_NAME
        const val REASON_SCHEDULE = PrefKeys.REASON_SCHEDULE
        const val REASON_TIME = PrefKeys.REASON_TIME
        const val REASON_MANUAL = PrefKeys.REASON_MANUAL
    }

    constructor(context: Context) : this(
        context.applicationContext,
        context.applicationContext.getSharedPreferences(
            PrefKeys.PREF_NAME,
            Context.MODE_PRIVATE
        )
    )

    // 系统时间篡改检测用（内存里维护最近一次观察值，最多每分钟落盘一次）
    private var lastWallCheck = 0L
    private var lastElapsedCheck = 0L
    private var lastStampPersist = 0L

    init {
        migrateLegacyTimer()
        migrateQuestionDefaults()
        migrateLegacyDefaults()
        lastWallCheck = prefs.getLong(PrefKeys.LAST_SEEN_WALL, 0)
        lastElapsedCheck = prefs.getLong(PrefKeys.LAST_SEEN_ELAPSED, 0)
        lastStampPersist = lastElapsedCheck
    }

    private fun migrateLegacyTimer() {
        if (!prefs.contains(PrefKeys.TIMER_END_TIME)) return
        val end = prefs.getLong(PrefKeys.TIMER_END_TIME, 0)
        val remaining = maxOf(0L, end - System.currentTimeMillis())
        prefs.edit()
            .putLong(PrefKeys.TIMER_REMAINING, remaining)
            .putLong(PrefKeys.TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(PrefKeys.TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(PrefKeys.TIMER_STAMP_WALL, System.currentTimeMillis())
            .remove(PrefKeys.TIMER_END_TIME)
            .apply()
    }

    private fun migrateQuestionDefaults() {
        if (!prefs.contains(PrefKeys.QUESTION_GRADE)) {
            prefs.edit()
                .putInt(PrefKeys.QUESTION_GRADE, PrefKeys.DEFAULT_QUESTION_GRADE)
                .apply()
        }
        if (!prefs.contains(PrefKeys.QUESTION_SUBJECTS)) {
            prefs.edit()
                .putString(
                    PrefKeys.QUESTION_SUBJECTS,
                    PrefKeys.DEFAULT_QUESTION_SUBJECTS
                )
                .apply()
        }
    }

    private fun migrateLegacyDefaults() {
        // 一次性迁移旧版存储默认值，必须有标志位：init 在每次构造 PrefManager 时都会执行，
        // 无标志则会把新版用户主动选的「五年级」/「只开拼音」静默重置回新默认
        if (prefs.getBoolean(PrefKeys.LEGACY_MIGRATED, false)) return
        if (prefs.contains(PrefKeys.QUESTION_GRADE) &&
            prefs.getInt(PrefKeys.QUESTION_GRADE, PrefKeys.DEFAULT_QUESTION_GRADE) == 5
        ) {
            prefs.edit()
                .putInt(PrefKeys.QUESTION_GRADE, PrefKeys.DEFAULT_QUESTION_GRADE)
                .apply()
        }
        if (prefs.contains(PrefKeys.QUESTION_SUBJECTS) &&
            prefs.getString(PrefKeys.QUESTION_SUBJECTS, "") == "pinyin"
        ) {
            prefs.edit()
                .putString(
                    PrefKeys.QUESTION_SUBJECTS,
                    PrefKeys.DEFAULT_QUESTION_SUBJECTS
                )
                .apply()
        }
        if (prefs.contains(PrefKeys.UNLOCK_COUNT) &&
            prefs.getInt(PrefKeys.UNLOCK_COUNT, PrefKeys.DEFAULT_UNLOCK_COUNT) == 3
        ) {
            prefs.edit()
                .putInt(PrefKeys.UNLOCK_COUNT, PrefKeys.DEFAULT_UNLOCK_COUNT)
                .apply()
        }
        prefs.edit().putBoolean(PrefKeys.LEGACY_MIGRATED, true).apply()
    }

    // ===== 每日总时长上限 =====
    // 0 表示不限制（默认，避免升级后静默改变老用户行为）。
    fun getDailyLimitMinutes(now: Calendar = Calendar.getInstance()): Int {
        val weekday = prefs.getInt(
            PrefKeys.DAILY_LIMIT_MIN,
            PrefKeys.DEFAULT_DAILY_LIMIT_MIN
        )
        val weekend = prefs.getInt(PrefKeys.DAILY_LIMIT_WEEKEND_MIN, -1)
        return DailyLimitPolicy.resolveLimit(weekday, weekend, isWeekend(now))
    }

    /** 周末额度原值：-1 表示跟随平日。 */
    fun getWeekendDailyLimitMinutes(): Int =
        prefs.getInt(PrefKeys.DAILY_LIMIT_WEEKEND_MIN, -1)

    /** 平日额度原值。 */
    fun getWeekdayDailyLimitMinutes(): Int =
        prefs.getInt(PrefKeys.DAILY_LIMIT_MIN, PrefKeys.DEFAULT_DAILY_LIMIT_MIN)

    fun setDailyLimitMinutes(minutes: Int) {
        prefs.edit()
            .putInt(PrefKeys.DAILY_LIMIT_MIN, minutes.coerceIn(0, 12 * 60))
            .apply()
    }

    /** 设置周末额度；传 -1 表示跟随平日额度。 */
    fun setWeekendDailyLimitMinutes(minutes: Int) {
        prefs.edit()
            .putInt(PrefKeys.DAILY_LIMIT_WEEKEND_MIN, minutes.coerceIn(-1, 12 * 60))
            .apply()
    }

    fun isWeekend(now: Calendar = Calendar.getInstance()): Boolean {
        val dow = now.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    fun getDailyUsedMs(): Long {
        rolloverDailyIfNeeded()
        return prefs.getLong(PrefKeys.DAILY_USED_MS, 0)
    }

    fun addDailyUsedMs(millis: Long) {
        if (millis <= 0) return
        rolloverDailyIfNeeded()
        val used = prefs.getLong(PrefKeys.DAILY_USED_MS, 0) + millis
        prefs.edit()
            .putLong(PrefKeys.DAILY_USED_MS, used)
            // 同步一份按天的历史记录，供「近 7 天使用记录」展示
            .putLong(PrefKeys.USAGE_PREFIX + todayKey(), used)
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
            out.add(key to prefs.getLong(PrefKeys.USAGE_PREFIX + key, 0))
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
            .putInt(PrefKeys.STARS, stars - need)
            .putInt(PrefKeys.BONUS_PENDING_MIN, getBonusPendingMinutes() + minutes)
            .apply()
        return true
    }

    fun getBonusPendingMinutes(): Int = prefs.getInt(PrefKeys.BONUS_PENDING_MIN, 0)

    /** 解锁时调用：把「待生效」奖励转成今天的额度。 */
    fun consumeBonusMinutes(): Int {
        val pending = getBonusPendingMinutes()
        if (pending <= 0) return 0
        rolloverDailyIfNeeded()
        prefs.edit()
            .putInt(PrefKeys.BONUS_PENDING_MIN, 0)
            .putInt(PrefKeys.DAILY_BONUS_MIN, getDailyBonusMinutes() + pending)
            .apply()
        return pending
    }

    fun getDailyBonusMinutes(): Int = prefs.getInt(PrefKeys.DAILY_BONUS_MIN, 0)

    /**
     * 家长 PIN 放行：临时不受每日上限约束（一次性，过点自动失效）。
     * 用于家长自己要继续使用、或孩子今天已经用完但确有需要的情况。
     */
    fun grantDailyBypass(minutes: Int) {
        if (minutes <= 0) return
        prefs.edit()
            .putLong(
                PrefKeys.DAILY_BYPASS_UNTIL,
                SystemClock.elapsedRealtime() + minutes * 60_000L
            )
            .apply()
    }

    fun isDailyBypassActive(): Boolean {
        val until = prefs.getLong(PrefKeys.DAILY_BYPASS_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
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
                .putInt(PrefKeys.DAILY_BONUS_MIN, getDailyBonusMinutes() + minutes)
                .apply()
            persistTimer(getRemainingMillis() + minutes * 60_000L)
        } else {
            prefs.edit()
                .putInt(PrefKeys.BONUS_PENDING_MIN, getBonusPendingMinutes() + minutes)
                .apply()
        }
    }

    /** 本次解锁实际可用分钟数：同时受单次观看时长、今日剩余额度与星星奖励约束。 */
    fun effectiveUnlockMinutes(): Int = DailyLimitPolicy.effectiveUnlockMinutes(
        getWatchLimitMinutes(),
        getBonusPendingMinutes(),
        getDailyRemainingMs()
    )

    private fun rolloverDailyIfNeeded() {
        val today = todayKey()
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val lastDay = prefs.getString(PrefKeys.LAST_SEEN_DAY, "") ?: ""

        // 协议外改时间：墙钟比单调时钟走得更快（超过 2 小时，且本次间隔 < 10 分钟），
        // 或日期直接回退，都视为篡改，保留当日已用额度不重置。
        var suspicious = false
        if (lastWallCheck > 0 && lastElapsedCheck > 0 && elapsed >= lastElapsedCheck) {
            val wallDelta = wall - lastWallCheck
            val elapsedDelta = elapsed - lastElapsedCheck
            if (ClockGuard.isSuspiciousJump(wallDelta, elapsedDelta)) {
                suspicious = true
            }
        }
        lastWallCheck = wall
        lastElapsedCheck = elapsed

        val rolledBack = lastDay.isNotEmpty() && today < lastDay
        if (rolledBack || suspicious) {
            if (!prefs.getBoolean(PrefKeys.CLOCK_ROLLBACK, false)) {
                prefs.edit().putBoolean(PrefKeys.CLOCK_ROLLBACK, true).apply()
                Diag.w("Clock", "time tampering detected: lastDay=$lastDay today=$today")
            }
            return
        }

        if (prefs.getString(PrefKeys.DAILY_DATE, "") != today) {
            prefs.edit()
                .putString(PrefKeys.DAILY_DATE, today)
                .putString(PrefKeys.LAST_SEEN_DAY, today)
                .putLong(PrefKeys.DAILY_USED_MS, 0)
                .putInt(PrefKeys.DAILY_BONUS_MIN, 0)
                .apply()
            lastStampPersist = elapsed
            prefs.edit()
                .putLong(PrefKeys.LAST_SEEN_WALL, wall)
                .putLong(PrefKeys.LAST_SEEN_ELAPSED, elapsed)
                .apply()
        } else if (lastDay != today || elapsed - lastStampPersist > 60_000) {
            lastStampPersist = elapsed
            prefs.edit()
                .putString(PrefKeys.LAST_SEEN_DAY, today)
                .putLong(PrefKeys.LAST_SEEN_WALL, wall)
                .putLong(PrefKeys.LAST_SEEN_ELAPSED, elapsed)
                .apply()
        }
    }

    /** 是否检测到系统时间被改回（家长确认后调用 clearClockRollback）。 */
    fun wasClockRolledBack(): Boolean = prefs.getBoolean(PrefKeys.CLOCK_ROLLBACK, false)

    fun clearClockRollback() {
        prefs.edit().remove(PrefKeys.CLOCK_ROLLBACK).apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getWatchLimitMinutes(): Int =
        prefs.getInt(PrefKeys.WATCH_LIMIT_MIN, PrefKeys.DEFAULT_WATCH_LIMIT_MIN)

    fun setWatchLimitMinutes(minutes: Int) {
        prefs.edit().putInt(PrefKeys.WATCH_LIMIT_MIN, minutes).apply()
    }

    fun isLocked(): Boolean = prefs.getBoolean(PrefKeys.IS_LOCKED, false)

    fun setLocked(locked: Boolean) {
        val editor = prefs.edit().putBoolean(PrefKeys.IS_LOCKED, locked)
        // 解锁时顺手清掉锁屏原因，避免下次误判为「时段锁」自动放行
        if (!locked) {
            editor.remove(PrefKeys.LOCKED_BY_SCHEDULE)
            editor.remove(PrefKeys.LOCK_REASON)
        }
        editor.apply()
    }

    /** 锁定原因：schedule / time / manual（空表示未知，按 time 处理）。 */
    fun setLockReason(reason: String) {
        prefs.edit()
            .putString(PrefKeys.LOCK_REASON, reason)
            .putBoolean(PrefKeys.LOCKED_BY_SCHEDULE, reason == REASON_SCHEDULE)
            .apply()
    }

    fun getLockReason(): String =
        prefs.getString(PrefKeys.LOCK_REASON, REASON_TIME) ?: REASON_TIME

    fun isLockedBySchedule(): Boolean = prefs.getBoolean(PrefKeys.LOCKED_BY_SCHEDULE, false)

    fun isTimerActive(): Boolean = prefs.getBoolean(PrefKeys.TIMER_ACTIVE, false)

    fun getRemainingMillis(): Long = prefs.getLong(PrefKeys.TIMER_REMAINING, 0)

    fun startTimer(minutes: Int) {
        prefs.edit()
            .putBoolean(PrefKeys.TIMER_ACTIVE, true)
            .putLong(PrefKeys.TIMER_REMAINING, minutes * 60_000L)
            .putLong(PrefKeys.TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(PrefKeys.TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(PrefKeys.TIMER_STAMP_WALL, System.currentTimeMillis())
            .putBoolean(PrefKeys.IS_LOCKED, false)
            .apply()
    }

    fun stopTimer() {
        prefs.edit()
            .putBoolean(PrefKeys.TIMER_ACTIVE, false)
            .putLong(PrefKeys.TIMER_REMAINING, 0)
            .putLong(PrefKeys.TIMER_STAMP_UPTIME, 0)
            .putLong(PrefKeys.TIMER_STAMP_ELAPSED, 0)
            .putLong(PrefKeys.TIMER_STAMP_WALL, 0)
            .remove(PrefKeys.TIMER_END_TIME)
            .apply()
    }

    fun isTimerExpired(): Boolean = isTimerActive() && getRemainingMillis() <= 0

    fun persistTimer(remainingMillis: Long) {
        prefs.edit()
            .putLong(PrefKeys.TIMER_REMAINING, remainingMillis)
            .putLong(PrefKeys.TIMER_STAMP_UPTIME, SystemClock.uptimeMillis())
            .putLong(PrefKeys.TIMER_STAMP_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(PrefKeys.TIMER_STAMP_WALL, System.currentTimeMillis())
            .apply()
    }

    fun deductOfflineIfAny() {
        if (!isTimerActive()) return
        val lastElapsed = prefs.getLong(PrefKeys.TIMER_STAMP_ELAPSED, 0)
        if (lastElapsed <= 0 || SystemClock.elapsedRealtime() < lastElapsed) {
            val wallStamp = prefs.getLong(PrefKeys.TIMER_STAMP_WALL, 0)
            var remaining = getRemainingMillis()
            val unbilled = if (wallStamp > 0 && remaining > 0) {
                queryForegroundMillisSince(wallStamp)
            } else {
                0
            }
            if (unbilled > 0) {
                remaining = maxOf(0L, remaining - unbilled)
                addDailyUsedMs(minOf(unbilled, getRemainingMillis()))
            }
            persistTimer(remaining)
            return
        }
        val awake = SystemClock.uptimeMillis() - prefs.getLong(PrefKeys.TIMER_STAMP_UPTIME, 0)
        if (awake <= 0) return
        val remaining = getRemainingMillis() - awake
        addDailyUsedMs(minOf(awake, getRemainingMillis()))
        persistTimer(maxOf(0L, remaining))
    }

    private fun queryForegroundMillisSince(wallStamp: Long): Long {
        val now = System.currentTimeMillis()
        var total = 0L
        try {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager ?: return 0
            val events = usm.queryEvents(wallStamp, now) ?: return 0
            val resumedAt = HashMap<String, Long>()
            val ev = UsageEvents.Event()
            while (events.hasNextEvent() && events.getNextEvent(ev)) {
                val pkg = ev.packageName ?: continue
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    resumedAt[pkg] = ev.timeStamp
                } else if (ev.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    val t0 = resumedAt.remove(pkg)
                    if (t0 != null) {
                        total += maxOf(0L, ev.timeStamp - maxOf(wallStamp, t0))
                    }
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

    fun setHomeAliasEnabled(context: Context, enabled: Boolean) {
        val alias = ComponentName(context, "com.kidslock.app.LockScreenHome")
        val pm = context.packageManager
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
    }
}
