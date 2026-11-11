package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Test

class Sm2Test {

    private val today = 20_000L

    @Test
    fun firstCorrectSchedulesOneDay() {
        val next = Sm2.review(Sm2.State(), correct = true, today = today)
        assertEquals(1, next.reps)
        assertEquals(1, next.intervalDays)
        assertEquals(today + 1, next.dueDay)
        assertEquals(2.5, next.ease, 0.0001)
    }

    @Test
    fun secondCorrectSchedulesSixDays() {
        val first = Sm2.review(Sm2.State(), true, today)
        val second = Sm2.review(first, true, today + 1)
        assertEquals(2, second.reps)
        assertEquals(6, second.intervalDays)
        assertEquals(today + 1 + 6, second.dueDay)
    }

    @Test
    fun thirdCorrectMultipliesByEase() {
        var state = Sm2.review(Sm2.State(), true, today)
        state = Sm2.review(state, true, today + 1)
        val third = Sm2.review(state, true, today + 7)
        assertEquals(15, third.intervalDays) // 6 * 2.5
    }

    @Test
    fun wrongResetsAndLowersEase() {
        var state = Sm2.review(Sm2.State(), true, today)
        state = Sm2.review(state, true, today + 1)
        val wrong = Sm2.review(state, false, today + 7)
        assertEquals(0, wrong.reps)
        assertEquals(0, wrong.intervalDays)
        assertEquals(today + 7, wrong.dueDay)
        assertEquals(1, wrong.lapses)
        assertEquals(2.3, wrong.ease, 0.0001)
    }

    @Test
    fun easeNeverDropsBelowMinimum() {
        var state = Sm2.State(ease = 1.35)
        repeat(5) { state = Sm2.review(state, false, today) }
        assertEquals(Sm2.MIN_EASE, state.ease, 0.0001)
    }
}
