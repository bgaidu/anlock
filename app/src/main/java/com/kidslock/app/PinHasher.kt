package com.kidslock.app

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    const val PBKDF2_ITERATIONS = 10000
    const val PIN_HASH_LENGTH_BITS = 256
    const val PIN_SALT_BYTES = 16

    fun newSalt(): ByteArray {
        val salt = ByteArray(PIN_SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun pbkdf2Hex(password: String, salt: ByteArray): String {
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PIN_HASH_LENGTH_BITS)
            val hash = skf.generateSecret(spec).encoded
            return bytesToHex(hash)
        } catch (e: NoSuchAlgorithmException) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(password.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(md.digest())
        } catch (e: Exception) {
            throw IllegalStateException("PBKDF2 not available", e)
        }
    }

    fun sha256Hex(s: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            return bytesToHex(md.digest(s.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 not available", e)
        }
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}