package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLimitPolicyTest {

    @Test
    fun resolveMinutes_weekendFollowsOrOverridesWeekday() {
        assertEquals(30, AppLimitPolicy.resolveMinutes(30, -1, isWeekend = false))
        assertEquals(30, AppLimitPolicy.resolveMinutes(30, -1, isWeekend = true))
        assertEquals(60, AppLimitPolicy.resolveMinutes(30, 60, isWeekend = true))
        assertEquals(-1, AppLimitPolicy.resolveMinutes(-1, 60, isWeekend = false))
    }

    @Test
    fun effectiveLimitMinutes_addsParentBonus() {
        assertEquals(40, AppLimitPolicy.effectiveLimitMinutes(30, 10))
        assertEquals(30, AppLimitPolicy.effectiveLimitMinutes(30, 0))
        assertEquals(AppLimitPolicy.MAX_MINUTES, AppLimitPolicy.effectiveLimitMinutes(30, 9999))
    }

    @Test
    fun effectiveLimitMinutes_blockedStaysBlocked() {
        assertEquals(AppLimitPolicy.BLOCKED, AppLimitPolicy.effectiveLimitMinutes(0, 30))
    }

    @Test
    fun effectiveLimitMinutes_unlimitedStaysUnlimited() {
        assertEquals(AppLimitPolicy.UNLIMITED, AppLimitPolicy.effectiveLimitMinutes(-1, 30))
    }

    @Test
    fun remainingMs_handlesUnlimitedBlockedAndNormal() {
        assertEquals(Long.MAX_VALUE, AppLimitPolicy.remainingMs(-1, 10 * 60_000L))
        assertEquals(0L, AppLimitPolicy.remainingMs(0, 0))
        assertEquals(20 * 60_000L, AppLimitPolicy.remainingMs(30, 10 * 60_000L))
        assertEquals(0L, AppLimitPolicy.remainingMs(30, 40 * 60_000L))
    }

    @Test
    fun isExhausted_boundaries() {
        assertFalse(AppLimitPolicy.isExhausted(-1, Long.MAX_VALUE / 2))
        assertTrue(AppLimitPolicy.isExhausted(0, 0))
        assertFalse(AppLimitPolicy.isExhausted(30, 29 * 60_000L))
        assertTrue(AppLimitPolicy.isExhausted(30, 30 * 60_000L))
        assertTrue(AppLimitPolicy.isExhausted(30, 31 * 60_000L))
    }

    @Test
    fun label_isHumanReadable() {
        assertEquals("不限", AppLimitPolicy.label(-1))
        assertEquals("禁止使用", AppLimitPolicy.label(0))
        assertEquals("15 分钟", AppLimitPolicy.label(15))
        assertEquals("1 小时", AppLimitPolicy.label(60))
        assertEquals("1 小时 30 分钟", AppLimitPolicy.label(90))
    }

    @Test
    fun formatUsage_roundsUpPartialMinutes() {
        assertEquals("0 分钟", AppLimitPolicy.formatUsage(0))
        assertEquals("1 分钟", AppLimitPolicy.formatUsage(1))
        assertEquals("2 小时 5 分钟", AppLimitPolicy.formatUsage(125 * 60_000L))
    }
}
