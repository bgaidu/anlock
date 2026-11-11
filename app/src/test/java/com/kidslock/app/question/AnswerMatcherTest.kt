package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {

    @Test
    fun exactIntegerMatches() {
        assertTrue(AnswerMatcher.matches("42", "42"))
    }

    @Test
    fun trimsSpacesAndFullWidthDigits() {
        assertTrue(AnswerMatcher.matches(" １２ ", "12"))
    }

    @Test
    fun decimalWithTrailingZerosMatches() {
        assertTrue(AnswerMatcher.matches("1.50", "1.5"))
        assertTrue(AnswerMatcher.matches("1.5", "1.50"))
    }

    @Test
    fun fractionCanBeReduced() {
        assertTrue(AnswerMatcher.matches("2/4", "1/2"))
        assertTrue(AnswerMatcher.matches("6/4", "3/2"))
    }

    @Test
    fun fractionEqualsDecimal() {
        assertTrue(AnswerMatcher.matches("0.5", "1/2"))
        assertTrue(AnswerMatcher.matches("1/2", "0.5"))
    }

    @Test
    fun wrongAnswersDoNotMatch() {
        assertFalse(AnswerMatcher.matches("41", "42"))
        assertFalse(AnswerMatcher.matches("1/3", "1/2"))
        assertFalse(AnswerMatcher.matches("", "42"))
        assertFalse(AnswerMatcher.matches(null, "42"))
    }

    @Test
    fun normalizeHandlesChinesePunctuation() {
        assertEquals("1.5", AnswerMatcher.normalize(" １．５ "))
        assertEquals("3/4", AnswerMatcher.normalize("3／4"))
    }
}
