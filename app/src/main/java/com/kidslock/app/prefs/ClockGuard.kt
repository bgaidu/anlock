package com.kidslock.app.prefs

/**
 * 系统时间篡改检测的纯判断：
 * 墙钟比单调时钟多走了 2 小时以上，且两次观察间隔很短，视为把时间往未来调。
 * 往回调日期由调用方用字符串比较另行判断。
 */
object ClockGuard {
    private const val MAX_WALL_LEAD_MS = 2 * 3600_000L
    private const val MAX_OBSERVE_GAP_MS = 10 * 60_000L

    fun isSuspiciousJump(wallDeltaMs: Long, elapsedDeltaMs: Long): Boolean =
        wallDeltaMs - elapsedDeltaMs > MAX_WALL_LEAD_MS &&
                elapsedDeltaMs < MAX_OBSERVE_GAP_MS
}
