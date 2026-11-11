package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDifficultyTest {

    @Test
    fun disabledAlwaysReturnsBaseGrade() {
        assertEquals(3, AdaptiveDifficulty.next(5, 3, 95, 20, enabled = false))
        assertEquals(3, AdaptiveDifficulty.next(1, 3, 10, 20, enabled = false))
    }

    @Test
    fun needsEnoughSamplesBeforeAdjusting() {
        assertEquals(3, AdaptiveDifficulty.next(3, 3, 95, 19, enabled = true))
    }

    @Test
    fun raisesOneGradeOnHighAccuracy() {
        assertEquals(4, AdaptiveDifficulty.next(3, 3, 90, 20, enabled = true))
    }

    @Test
    fun lowersOneGradeOnLowAccuracy() {
        assertEquals(2, AdaptiveDifficulty.next(3, 3, 40, 20, enabled = true))
    }

    @Test
    fun keepsGradeInMiddleBand() {
        assertEquals(3, AdaptiveDifficulty.next(3, 3, 70, 20, enabled = true))
    }

    @Test
    fun neverExceedsOneGradeFromBase() {
        // 已经上调过一档，再高也只能停在 base+1
        assertEquals(4, AdaptiveDifficulty.next(4, 3, 100, 20, enabled = true))
        // 已经下调过一档，再低也只能停在 base-1
        assertEquals(2, AdaptiveDifficulty.next(2, 3, 0, 20, enabled = true))
    }

    @Test
    fun clampsAtGradeBounds() {
        assertEquals(0, AdaptiveDifficulty.next(0, 0, 0, 20, enabled = true))
        assertEquals(6, AdaptiveDifficulty.next(6, 6, 100, 20, enabled = true))
    }

    @Test
    fun rangeIsClampedToValidGrades() {
        assertEquals(0..1, AdaptiveDifficulty.range(0))
        assertEquals(5..6, AdaptiveDifficulty.range(6))
        assertEquals(2..4, AdaptiveDifficulty.range(3))
    }
}
