package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQuotaTest {

    @Test
    fun third_isOneThirdWithCapFive() {
        assertEquals(5, ReviewQuota.limit(15, ReviewQuota.THIRD))
        assertEquals(2, ReviewQuota.limit(6, ReviewQuota.THIRD))
        assertEquals(1, ReviewQuota.limit(2, ReviewQuota.THIRD))
        assertEquals(1, ReviewQuota.limit(1, ReviewQuota.THIRD))
        assertEquals(0, ReviewQuota.limit(0, ReviewQuota.THIRD))
    }

    @Test
    fun half_roundsUpWithCapEight() {
        assertEquals(8, ReviewQuota.limit(15, ReviewQuota.HALF))
        assertEquals(5, ReviewQuota.limit(10, ReviewQuota.HALF))
        assertEquals(1, ReviewQuota.limit(1, ReviewQuota.HALF))
    }

    @Test
    fun all_usesEveryScheduledQuestion() {
        assertEquals(15, ReviewQuota.limit(15, ReviewQuota.ALL))
        assertEquals(1, ReviewQuota.limit(1, ReviewQuota.ALL))
        assertEquals(0, ReviewQuota.limit(0, ReviewQuota.ALL))
    }

    @Test
    fun invalidRatioFallsBackToThird() {
        assertEquals(ReviewQuota.THIRD, ReviewQuota.sanitize(0))
        assertEquals(ReviewQuota.THIRD, ReviewQuota.sanitize(7))
        assertEquals(ReviewQuota.HALF, ReviewQuota.sanitize(2))
        assertEquals(ReviewQuota.ALL, ReviewQuota.sanitize(1))
    }

    @Test
    fun labels() {
        assertEquals("1/3", ReviewQuota.label(3))
        assertEquals("1/2", ReviewQuota.label(2))
        assertEquals("全部", ReviewQuota.label(1))
        assertEquals("1/3", ReviewQuota.label(99))
    }
}
