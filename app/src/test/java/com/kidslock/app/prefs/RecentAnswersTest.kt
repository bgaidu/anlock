package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAnswersTest {

    @Test
    fun append_keepsOnlyLatestWindow() {
        var history = ""
        repeat(25) { i -> history = RecentAnswers.append(history, i % 2 == 0) }
        assertEquals(RecentAnswers.WINDOW, history.length)
        // 偶数题正确：第 0..24 题，保留最后 20 题（第 5..24 题）
        assertEquals("01010101010101010101", history)
    }

    @Test
    fun append_respectsCustomWindow() {
        var history = ""
        repeat(5) { history = RecentAnswers.append(history, true, window = 3) }
        assertEquals("111", history)
    }

    @Test
    fun accuracy_insufficientSamples_returnsMinusOne() {
        assertEquals(-1, RecentAnswers.accuracy(null))
        assertEquals(-1, RecentAnswers.accuracy(""))
        assertEquals(-1, RecentAnswers.accuracy("1111"))
    }

    @Test
    fun accuracy_computesRoundedPercent() {
        assertEquals(50, RecentAnswers.accuracy("1".repeat(10) + "0".repeat(10)))
        assertEquals(100, RecentAnswers.accuracy("1".repeat(20)))
        assertEquals(0, RecentAnswers.accuracy("0".repeat(20)))
        assertEquals(67, RecentAnswers.accuracy("1".repeat(4) + "0".repeat(2), window = 6))
    }

    @Test
    fun accuracy_onlyUsesLatestWindow() {
        val history = "0".repeat(20) + "1".repeat(20)
        assertEquals(100, RecentAnswers.accuracy(history))
    }

    @Test
    fun answeredCount_countsRawCharacters() {
        assertEquals(0, RecentAnswers.answeredCount(null))
        assertEquals(7, RecentAnswers.answeredCount("1010101"))
    }
}
