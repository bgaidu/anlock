package com.kidslock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerProgressTest {

    @Test
    fun correctIncrementsProgress() {
        var p = AnswerProgress(required = 5)
        p = p.onCorrect()
        p = p.onCorrect()
        assertEquals(2, p.correct)
        assertEquals(5, p.required)
        assertEquals(3, p.remaining)
        assertFalse(p.unlocked)
    }

    @Test
    fun wrongKeepsProgressAndTarget() {
        var p = AnswerProgress(required = 5).onCorrect().onCorrect()
        p = p.onWrong()
        assertEquals(2, p.correct)
        assertEquals(5, p.required)
        assertEquals(3, p.remaining)
        assertFalse(p.unlocked)
    }

    @Test
    fun repeatedWrongAnswersNeverResetProgress() {
        var p = AnswerProgress(required = 5)
        repeat(3) { p = p.onWrong() }
        assertEquals(0, p.correct)
        assertEquals(5, p.required)
        assertEquals(5, p.remaining)
    }

    @Test
    fun unlockOnlyWhenCorrectReachesRequired() {
        var p = AnswerProgress(required = 2).onCorrect()
        assertFalse(p.unlocked)
        p = p.onCorrect()
        assertTrue(p.unlocked)
        assertEquals(0, p.remaining)
    }

    @Test
    fun wrongAfterNearUnlockStillNeedsOnlyTheSetTarget() {
        var p = AnswerProgress(required = 3).onCorrect().onCorrect()
        p = p.onWrong()
        assertFalse(p.unlocked)
        assertEquals(2, p.correct)
        assertEquals(3, p.required)
        p = p.onCorrect()
        assertTrue(p.unlocked)
    }
}
