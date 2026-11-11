package com.kidslock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleMathTest {

    @Test
    fun normalWindow() {
        assertTrue(ScheduleMath.isWithin(480, 1260, 480))
        assertTrue(ScheduleMath.isWithin(480, 1260, 1259))
        assertFalse(ScheduleMath.isWithin(480, 1260, 1260))
        assertFalse(ScheduleMath.isWithin(480, 1260, 300))
    }

    @Test
    fun crossMidnightWindow() {
        assertTrue(ScheduleMath.isWithin(1200, 420, 1300))
        assertTrue(ScheduleMath.isWithin(1200, 420, 60))
        assertFalse(ScheduleMath.isWithin(1200, 420, 480))
    }

    @Test
    fun unsetDayMeansUnlimited() {
        assertTrue(ScheduleMath.isWithin(-1, -1, 600))
        assertTrue(ScheduleMath.isWithin(480, 480, 600))
    }

    @Test
    fun dayIndexMondayToSunday() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.NOVEMBER, 9, 12, 0, 0) // 2026-11-09 是周一
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals(0, ScheduleMath.dayIndex(cal))
        cal.add(Calendar.DAY_OF_YEAR, 6) // 周日
        assertEquals(6, ScheduleMath.dayIndex(cal))
    }

    @Test
    fun parseAndFormat() {
        assertEquals(8 * 60 + 30, ScheduleMath.parse("08:30"))
        assertEquals(23 * 60 + 59, ScheduleMath.parse("23:59"))
        assertNull(ScheduleMath.parse("24:00"))
        assertNull(ScheduleMath.parse("8:5"))
        assertEquals("08:30", ScheduleMath.format(8 * 60 + 30))
    }

    @Test
    fun nextBoundaryWithinSameDay() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 9, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = ScheduleMath.nextBoundaryMillis(now.timeInMillis) { 8 * 60 to 21 * 60 }
        val expected = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals(expected, next)
    }

    @Test
    fun nextBoundaryGoesToTomorrowStart() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 9, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = ScheduleMath.nextBoundaryMillis(now.timeInMillis) { 8 * 60 to 21 * 60 }
        val expected = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals(expected, next)
    }

    @Test
    fun nextBoundaryHandlesCrossMidnightEnd() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 9, 21, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = ScheduleMath.nextBoundaryMillis(now.timeInMillis) { 20 * 60 to 7 * 60 }
        val expected = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals(expected, next)
    }

    @Test
    fun noWindowReturnsNull() {
        assertNull(ScheduleMath.nextBoundaryMillis(System.currentTimeMillis()) { -1 to -1 })
    }
}
