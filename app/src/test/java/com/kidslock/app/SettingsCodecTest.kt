package com.kidslock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    private fun assertRoundTrip(value: Any) {
        val encoded = SettingsCodec.encode(value)
        val decoded = SettingsCodec.decode(encoded.type, encoded.value)
        assertEquals(value, decoded)
    }

    @Test
    fun roundTrip_allSupportedTypes() {
        assertRoundTrip(true)
        assertRoundTrip(false)
        assertRoundTrip(42)
        assertRoundTrip(1_700_000_000_000L)
        assertRoundTrip(1.5f)
        assertRoundTrip("数学 · 三年级")
        assertRoundTrip(linkedSetOf("pinyin", "math", "english"))
    }

    @Test
    fun decode_acceptsLegacyJsonShapes() {
        // 老备份里数字可能被 JSON 解析成整数/长整数，集合是数组
        assertEquals(7, SettingsCodec.decode("i", 7))
        assertEquals(7L, SettingsCodec.decode("l", 7))
        assertEquals(2.5f, SettingsCodec.decode("f", 2.5))
        assertEquals("abc", SettingsCodec.decode("s", "abc"))
        assertEquals(
            listOf("a", "b"),
            (SettingsCodec.decode("ss", listOf("a", "b")) as Set<*>).toList()
        )
        assertEquals(true, SettingsCodec.decode("b", "true"))
    }

    @Test
    fun decode_unknownType_returnsNull() {
        assertNull(SettingsCodec.decode("???", 1))
        assertNull(SettingsCodec.decode("ss", 123))
    }

    @Test
    fun allowedKeys_includeNewSettings() {
        assertTrue(SettingsCodec.isAllowed("question_grade_subject_math"))
        assertTrue(SettingsCodec.isAllowed("question_grade_subject_english"))
        assertTrue(SettingsCodec.isAllowed("eye_rest_enabled"))
        assertTrue(SettingsCodec.isAllowed("eye_rest_interval_min"))
        assertTrue(SettingsCodec.isAllowed("schedule_start_0"))
        assertTrue(SettingsCodec.isAllowed("schedule_end_6"))
        // PIN 永远不进备份
        assertFalse(SettingsCodec.isAllowed("parent_pin"))
        assertFalse(SettingsCodec.isAllowed("pin_salt"))
        assertFalse(SettingsCodec.isAllowed("lan_token"))
    }
}
