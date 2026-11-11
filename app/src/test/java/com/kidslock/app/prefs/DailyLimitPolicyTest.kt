package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyLimitPolicyTest {

    @Test
    fun resolveLimit_weekdayUsesWeekdayValue() {
        assertEquals(60, DailyLimitPolicy.resolveLimit(60, 120, isWeekend = false))
        assertEquals(0, DailyLimitPolicy.resolveLimit(0, 120, isWeekend = false))
    }

    @Test
    fun resolveLimit_weekendFollowsWeekdayWhenUnset() {
        assertEquals(60, DailyLimitPolicy.resolveLimit(60, -1, isWeekend = true))
        assertEquals(60, DailyLimitPolicy.resolveLimit(60, -5, isWeekend = true))
    }

    @Test
    fun resolveLimit_weekendUsesOwnValue() {
        assertEquals(120, DailyLimitPolicy.resolveLimit(60, 120, isWeekend = true))
        assertEquals(0, DailyLimitPolicy.resolveLimit(60, 0, isWeekend = true))
    }

    @Test
    fun effectiveUnlockMinutes_noDailyLimit_returnsWatchPlusBonus() {
        assertEquals(
            40,
            DailyLimitPolicy.effectiveUnlockMinutes(30, 10, Long.MAX_VALUE)
        )
    }

    @Test
    fun effectiveUnlockMinutes_clampsToDailyRemaining() {
        assertEquals(20, DailyLimitPolicy.effectiveUnlockMinutes(30, 10, 20 * 60_000L))
        assertEquals(0, DailyLimitPolicy.effectiveUnlockMinutes(30, 10, 0))
        assertEquals(0, DailyLimitPolicy.effectiveUnlockMinutes(30, 10, -1))
    }

    @Test
    fun effectiveUnlockMinutes_roundsPartialMinuteUp() {
        // 剩 5 秒也应允许 1 分钟，由计时器按秒扣减时再夹紧
        assertEquals(1, DailyLimitPolicy.effectiveUnlockMinutes(30, 10, 5_000L))
        assertEquals(1, DailyLimitPolicy.effectiveUnlockMinutes(1, 0, 60_001L))
    }

    @Test
    fun effectiveUnlockMinutes_zeroWatchLimitStillAllowsBonus() {
        assertEquals(10, DailyLimitPolicy.effectiveUnlockMinutes(0, 10, Long.MAX_VALUE))
    }
}
