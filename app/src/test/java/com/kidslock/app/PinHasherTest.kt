package com.kidslock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PinHasherTest {

    @Test
    fun pbkdf2_hexIs64CharsAndDeterministic() {
        val salt = PinHasher.newSalt()
        val a = PinHasher.pbkdf2Hex("1234", salt)
        val b = PinHasher.pbkdf2Hex("1234", salt)
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun pbkdf2_differentSaltDiffers() {
        val a = PinHasher.pbkdf2Hex("1234", PinHasher.newSalt())
        val b = PinHasher.pbkdf2Hex("1234", PinHasher.newSalt())
        assertNotEquals(a, b)
    }

    @Test
    fun pbkdf2_differentPinDiffers() {
        val salt = PinHasher.newSalt()
        assertNotEquals(PinHasher.pbkdf2Hex("1234", salt), PinHasher.pbkdf2Hex("0000", salt))
    }

    @Test
    fun storedFormat_salt32PlusHash64() {
        val salt = PinHasher.newSalt()
        val saltHex = PinHasher.bytesToHex(salt)
        val hashHex = PinHasher.pbkdf2Hex("1234", salt)
        val stored = saltHex + hashHex
        assertEquals(96, stored.length)
        val saltFromStored = PinHasher.hexToBytes(stored.substring(0, 32))
        assertEquals(hashHex, PinHasher.pbkdf2Hex("1234", saltFromStored))
    }

    @Test
    fun hexRoundtrip() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10, -1, -128)
        val hex = PinHasher.bytesToHex(bytes)
        assertEquals(bytes.toList(), PinHasher.hexToBytes(hex).toList())
    }

    @Test
    fun newSalt_is16BytesAndUnique() {
        val s1 = PinHasher.newSalt()
        val s2 = PinHasher.newSalt()
        assertEquals(16, s1.size)
        assertNotEquals(s1.toList(), s2.toList())
    }

    @Test
    fun sha256Hex_is64CharsAndDeterministic() {
        assertEquals(64, PinHasher.sha256Hex("1234").length)
        assertEquals(PinHasher.sha256Hex("1234"), PinHasher.sha256Hex("1234"))
        assertNotEquals(PinHasher.sha256Hex("1234"), PinHasher.sha256Hex("0000"))
    }
}