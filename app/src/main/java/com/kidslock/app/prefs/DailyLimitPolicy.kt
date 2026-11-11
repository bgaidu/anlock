package com.kidslock.app.prefs

/** 每日额度与本次解锁时长的纯计算，便于 JVM 单测覆盖边界。 */
object DailyLimitPolicy {
    const val FOLLOW_WEEKDAY = -1

    fun resolveLimit(weekdayLimit: Int, weekendLimit: Int, isWeekend: Boolean): Int =
        if (!isWeekend) weekdayLimit
        else if (weekendLimit < 0) weekdayLimit
        else weekendLimit

    /**
     * 本次解锁实际可用分钟数：同时受单次观看时长、星星奖励与今日剩余额度约束。
     * dailyRemainingMs == Long.MAX_VALUE 表示未设每日上限。
     */
    fun effectiveUnlockMinutes(
        watchLimitMinutes: Int,
        pendingBonusMinutes: Int,
        dailyRemainingMs: Long
    ): Int {
        if (dailyRemainingMs <= 0) return 0
        val base = watchLimitMinutes + pendingBonusMinutes
        if (dailyRemainingMs == Long.MAX_VALUE) return base
        // 向上取整到分钟，精确到秒的余量由 TimerManager 每秒扣减时再夹紧
        val minutes = ((dailyRemainingMs + 59_999) / 60_000).toInt()
        return minOf(base, minutes).coerceAtLeast(1)
    }
}
