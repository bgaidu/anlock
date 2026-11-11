package com.kidslock.app.prefs

/**
 * 应用级限额的纯计算。
 *
 * 约定：
 * - 分钟数 `-1` 表示不限，`0` 表示禁止使用（任何时长都不允许）；
 * - 周末值 `-1` 表示跟随平日；
 * - 家长加时（bonus）只对有时长上限的应用生效，不会把「禁止使用」变成可用。
 */
object AppLimitPolicy {
    const val UNLIMITED = -1
    const val BLOCKED = 0
    const val MAX_MINUTES = 24 * 60

    fun resolveMinutes(weekday: Int, weekend: Int, isWeekend: Boolean): Int =
        if (!isWeekend) weekday else if (weekend < 0) weekday else weekend

    /** 叠加家长当日加时后的实际额度。 */
    fun effectiveLimitMinutes(baseMinutes: Int, bonusMinutes: Int): Int = when {
        baseMinutes < 0 -> UNLIMITED
        baseMinutes == 0 -> BLOCKED
        else -> (baseMinutes + bonusMinutes.coerceAtLeast(0)).coerceAtMost(MAX_MINUTES)
    }

    fun remainingMs(limitMinutes: Int, usedMs: Long): Long = when {
        limitMinutes < 0 -> Long.MAX_VALUE
        limitMinutes == 0 -> 0L
        else -> (limitMinutes * 60_000L - usedMs.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun isExhausted(limitMinutes: Int, usedMs: Long): Boolean = when {
        limitMinutes < 0 -> false
        limitMinutes == 0 -> true
        else -> usedMs.coerceAtLeast(0) >= limitMinutes * 60_000L
    }

    fun label(minutes: Int): String = when {
        minutes < 0 -> "不限"
        minutes == 0 -> "禁止使用"
        minutes < 60 -> "$minutes 分钟"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "$h 小时" else "$h 小时 $m 分钟"
        }
    }

    fun formatUsage(millis: Long): String {
        val minutes = ((millis.coerceAtLeast(0) + 59_999) / 60_000).toInt()
        return if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}
