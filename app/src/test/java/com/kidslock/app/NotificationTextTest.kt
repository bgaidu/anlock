package com.kidslock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {

    @Test
    fun remaining_roundsUpToWholeMinutes() {
        assertEquals("剩余观看时间：约 1 分钟", NotificationText.remaining(1L))
        assertEquals("剩余观看时间：约 1 分钟", NotificationText.remaining(59_999L))
        assertEquals("剩余观看时间：约 1 分钟", NotificationText.remaining(60_000L))
        assertEquals("剩余观看时间：约 2 分钟", NotificationText.remaining(60_001L))
        assertEquals("剩余观看时间：约 8 分钟", NotificationText.remaining(8 * 60_000L))
        assertEquals("剩余观看时间：约 9 分钟", NotificationText.remaining(8 * 60_000L + 1))
        assertEquals("剩余观看时间：约 120 分钟", NotificationText.remaining(120 * 60_000L))
    }

    @Test
    fun remaining_isLessThanOneMinuteWhenExpiredOrNearly() {
        assertEquals("剩余观看时间不足 1 分钟", NotificationText.remaining(0L))
        assertEquals("剩余观看时间不足 1 分钟", NotificationText.remaining(-5_000L))
    }

    @Test
    fun remaining_textStaysSameWithinTheSameMinute() {
        // 同一分钟内的每一秒都产生相同文案：通知不需要刷新
        val base = 7 * 60_000L + 3_000L
        val first = NotificationText.remaining(base)
        for (second in 1..50) {
            assertEquals(first, NotificationText.remaining(base + second * 1000L))
        }
        // 跨到下一分钟才变化
        assertTrue(first != NotificationText.remaining(base + 60_000L))
    }

    @Test
    fun gate_skipsRepeatedText() {
        val gate = NotificationRefreshGate()
        assertTrue(gate.shouldRefresh("剩余观看时间：约 8 分钟"))
        assertFalse(gate.shouldRefresh("剩余观看时间：约 8 分钟"))
        assertFalse(gate.shouldRefresh("剩余观看时间：约 8 分钟"))
        assertEquals("剩余观看时间：约 8 分钟", gate.lastText)
    }

    @Test
    fun gate_refreshesWhenTextChanges() {
        val gate = NotificationRefreshGate()
        assertTrue(gate.shouldRefresh("剩余观看时间：约 8 分钟"))
        assertTrue(gate.shouldRefresh("剩余观看时间：约 7 分钟"))
        assertTrue(gate.shouldRefresh("设备已锁定，答对汉字即可解锁"))
        assertFalse(gate.shouldRefresh("设备已锁定，答对汉字即可解锁"))
    }

    @Test
    fun gate_resetSeedsLastText() {
        val gate = NotificationRefreshGate()
        gate.reset("时段内自由使用（仅时段限制）· 今天 08:00-20:00")
        assertFalse(gate.shouldRefresh("时段内自由使用（仅时段限制）· 今天 08:00-20:00"))
        assertTrue(gate.shouldRefresh("计时结束"))

        gate.reset()
        assertNull(gate.lastText)
        assertTrue(gate.shouldRefresh("计时结束"))
    }
}
