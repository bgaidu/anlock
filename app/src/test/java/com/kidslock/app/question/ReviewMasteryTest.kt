package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMasteryTest {

    private val today = 20_000L

    @Test
    fun dueDayTodayOrPast_isDueEvenIfStatsLookMastered() {
        assertEquals(
            ReviewMastery.Status.DUE,
            ReviewMastery.status(reps = 6, intervalDays = 30, dueDay = today, todayDay = today)
        )
        assertEquals(
            ReviewMastery.Status.DUE,
            ReviewMastery.status(reps = 6, intervalDays = 30, dueDay = today - 3, todayDay = today)
        )
    }

    @Test
    fun futureDueWithEnoughRepsAndInterval_isMastered() {
        assertEquals(
            ReviewMastery.Status.MASTERED,
            ReviewMastery.status(
                reps = ReviewMastery.MASTERED_MIN_REPS,
                intervalDays = ReviewMastery.MASTERED_MIN_INTERVAL,
                dueDay = today + 1,
                todayDay = today
            )
        )
        assertTrue(
            ReviewMastery.isMastered(
                ReviewMastery.MASTERED_MIN_REPS,
                ReviewMastery.MASTERED_MIN_INTERVAL,
                today + 21,
                today
            )
        )
    }

    @Test
    fun futureDueWithWeakStats_isScheduled() {
        assertEquals(
            ReviewMastery.Status.SCHEDULED,
            ReviewMastery.status(reps = 2, intervalDays = 6, dueDay = today + 6, todayDay = today)
        )
        // 间隔够了但复习次数不足
        assertEquals(
            ReviewMastery.Status.SCHEDULED,
            ReviewMastery.status(reps = 3, intervalDays = 30, dueDay = today + 30, todayDay = today)
        )
        // 次数够了但间隔不足
        assertEquals(
            ReviewMastery.Status.SCHEDULED,
            ReviewMastery.status(reps = 5, intervalDays = 6, dueDay = today + 6, todayDay = today)
        )
    }

    @Test
    fun manualMasteredState_getsLongInterval() {
        val state = ReviewMastery.masteredState(today)
        assertEquals(ReviewMastery.MASTERED_MIN_REPS, state.reps)
        assertEquals(ReviewMastery.MANUAL_INTERVAL_DAYS, state.intervalDays)
        assertEquals(today + ReviewMastery.MANUAL_INTERVAL_DAYS, state.dueDay)
        assertTrue(ReviewMastery.isMastered(state.reps, state.intervalDays, state.dueDay, today))
    }

    @Test
    fun restoreState_becomesDueToday() {
        val state = ReviewMastery.restoreState(today)
        assertEquals(0, state.reps)
        assertEquals(0, state.intervalDays)
        assertEquals(today, state.dueDay)
        assertFalse(ReviewMastery.isMastered(state.reps, state.intervalDays, state.dueDay, today))
    }

    @Test
    fun labelsAreChinese() {
        assertEquals("待复习", ReviewMastery.label(ReviewMastery.Status.DUE))
        assertEquals("已排期", ReviewMastery.label(ReviewMastery.Status.SCHEDULED))
        assertEquals("已掌握", ReviewMastery.label(ReviewMastery.Status.MASTERED))
    }
}
