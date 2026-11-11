package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectSelectionTest {

    @Test
    fun parse_nullOrEmpty_fallsBackToDefault() {
        assertEquals(linkedSetOf("pinyin", "math"), SubjectSelection.parse(null))
        assertEquals(linkedSetOf("pinyin", "math"), SubjectSelection.parse(""))
        assertEquals(linkedSetOf("pinyin", "math"), SubjectSelection.parse(" , "))
    }

    @Test
    fun parse_filtersUnknownValuesAndTrims() {
        assertEquals(
            linkedSetOf("math", "pinyin"),
            SubjectSelection.parse(" math , spoken , pinyin ")
        )
    }

    @Test
    fun parse_allInvalid_fallsBackToDefault() {
        assertEquals(linkedSetOf("pinyin", "math"), SubjectSelection.parse("spoken,music"))
    }

    @Test
    fun encode_ordersByCanonicalSubjectOrder() {
        assertEquals(
            "pinyin,math,english,ciyu",
            SubjectSelection.encode(setOf("ciyu", "english", "math", "pinyin"))
        )
        assertEquals("english", SubjectSelection.encode(setOf("english")))
        assertEquals("", SubjectSelection.encode(emptySet()))
    }

    @Test
    fun roundTrip_dropsDuplicatesAndKeepsAllValidSubjects() {
        val parsed = SubjectSelection.parse("math,math,english")
        assertEquals(linkedSetOf("math", "english"), parsed)
        assertEquals("math,english", SubjectSelection.encode(parsed))
    }

    @Test
    fun defaults_areSane() {
        assertTrue(SubjectSelection.DEFAULT.contains("pinyin"))
        assertTrue(SubjectSelection.DEFAULT.contains("math"))
        assertFalse(SubjectSelection.DEFAULT.contains("english"))
    }
}
