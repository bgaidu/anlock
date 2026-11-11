package com.kidslock.app.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockGuardTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun detectsLargeFutureJumpWithinShortObservationGap() {
        assertTrue(ClockGuard.isSuspiciousJump(wallDeltaMs = 3 * hour, elapsedDeltaMs = minute))
    }

    @Test
    fun ignoresNormalClockDrift() {
        assertFalse(ClockGuard.isSuspiciousJump(wallDeltaMs = minute, elapsedDeltaMs = minute))
        // 即使有几秒的系统校准，也不该超过 2 小时阈值
        assertFalse(ClockGuard.isSuspiciousJump(wallDeltaMs = 2 * hour, elapsedDeltaMs = minute))
    }

    @Test
    fun ignoresLongObservationGap() {
        // 观察间隔本身超过 10 分钟时，墙钟多走的部分可以用休眠/没落盘解释
        assertFalse(ClockGuard.isSuspiciousJump(wallDeltaMs = 5 * hour, elapsedDeltaMs = 20 * minute))
    }

    @Test
    fun ignoresBackwardWallClock() {
        // 往回调由调用方按日期字符串另行判断
        assertFalse(ClockGuard.isSuspiciousJump(wallDeltaMs = -hour, elapsedDeltaMs = minute))
    }
}
