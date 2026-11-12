package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudyStatsPolicyTest {

    @Test
    fun accuracyPercent_handlesEmptyAndRounding() {
        assertEquals(-1, StudyStatsPolicy.accuracyPercent(0, 0))
        assertEquals(75, StudyStatsPolicy.accuracyPercent(4, 3))
        assertEquals(100, StudyStatsPolicy.accuracyPercent(5, 5))
        // 异常数据不应出现负数或超过 100
        assertEquals(0, StudyStatsPolicy.accuracyPercent(4, -1))
        assertEquals(100, StudyStatsPolicy.accuracyPercent(4, 9))
    }

    @Test
    fun currentStreak_countsTodayAndEarlier() {
        assertEquals(3, StudyStatsPolicy.currentStreak(setOf(0, 1, 2)))
        assertEquals(0, StudyStatsPolicy.currentStreak(emptySet()))
    }

    @Test
    fun currentStreak_doesNotBreakWhenTodayIsNotDoneYet() {
        // 今天还没答题，昨天及之前连续 2 天
        assertEquals(2, StudyStatsPolicy.currentStreak(setOf(1, 2)))
    }

    @Test
    fun currentStreak_breaksOnGap() {
        assertEquals(1, StudyStatsPolicy.currentStreak(setOf(0, 2, 3)))
    }

    @Test
    fun weakestSubject_ignoresSmallSamples() {
        val math = SubjectStudyStat("math", total = 4, correct = 1)
        assertNull(StudyStatsPolicy.weakestSubject(listOf(math)))
    }

    @Test
    fun weakestSubject_returnsLowestBelowThreshold() {
        val pinyin = SubjectStudyStat("pinyin", total = 10, correct = 9) // 90%
        val math = SubjectStudyStat("math", total = 10, correct = 6)   // 60%
        val english = SubjectStudyStat("english", total = 10, correct = 7) // 70%
        val weak = StudyStatsPolicy.weakestSubject(listOf(pinyin, math, english))
        assertSame(math, weak)
    }

    @Test
    fun weakestSubject_returnsNullWhenAllHealthy() {
        val pinyin = SubjectStudyStat("pinyin", total = 10, correct = 9)
        val math = SubjectStudyStat("math", total = 10, correct = 8)
        assertNull(StudyStatsPolicy.weakestSubject(listOf(pinyin, math)))
    }
}
