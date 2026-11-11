package com.kidslock.app

import java.util.Calendar
import java.util.Locale

/**
 * 使用时段的纯计算逻辑（可单元测试）：
 * 周一~周日的下标、跨零点判断、下一次时段切换时刻。
 */
object ScheduleMath {

    /** 0=周一 … 6=周日 */
    fun dayIndex(calendar: Calendar): Int {
        val dow = calendar.get(Calendar.DAY_OF_WEEK) // 1=周日 … 7=周六
        return if (dow == Calendar.SUNDAY) 6 else dow - 2
    }

    /**
     * minuteOfDay 是否落在 [startMin, endMin) 内。
     * start > end 表示跨零点（如 20:00–07:00）；任一为负或相等表示当天不限。
     */
    fun isWithin(startMin: Int, endMin: Int, minuteOfDay: Int): Boolean {
        if (startMin !in 0..1439 || endMin !in 0..1439 || startMin == endMin) return true
        return if (startMin <= endMin) {
            minuteOfDay in startMin until endMin
        } else {
            minuteOfDay >= startMin || minuteOfDay < endMin
        }
    }

    fun format(minuteOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

    fun parse(text: String): Int? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").find(text.trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    /**
     * 下一次时段开始/结束的墙钟毫秒时间。
     * windowForDay(day) 返回 (start,end)，-1 表示该天不限；全部不限时返回 null。
     */
    fun nextBoundaryMillis(nowMillis: Long, windowForDay: (Int) -> Pair<Int, Int>): Long? {
        val base = Calendar.getInstance()
        base.timeInMillis = nowMillis
        var best: Long? = null
        for (offset in 0..8) {
            val day = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val idx = dayIndex(day)
            val (start, end) = windowForDay(idx)
            if (start !in 0..1439 || end !in 0..1439 || start == end) continue

            val startAt = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, start / 60)
                set(Calendar.MINUTE, start % 60)
            }.timeInMillis
            // 跨零点时结束时间落在次日
            val endDay = if (start < end) day else (day.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val endAt = (endDay.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, end / 60)
                set(Calendar.MINUTE, end % 60)
            }.timeInMillis

            for (candidate in longArrayOf(startAt, endAt)) {
                if (candidate > nowMillis && (best == null || candidate < best!!)) {
                    best = candidate
                }
            }
        }
        return best
    }
}
