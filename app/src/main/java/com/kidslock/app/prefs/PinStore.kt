package com.kidslock.app.prefs

import android.content.SharedPreferences
import com.kidslock.app.PinHasher
import com.kidslock.app.prefs.PrefKeys.DEFAULT_PARENT_PIN
import com.kidslock.app.prefs.PrefKeys.PIN_FAIL_COUNT
import com.kidslock.app.prefs.PrefKeys.PIN_LOCK_UNTIL
import com.kidslock.app.prefs.PrefKeys.PIN_MODIFIED
import com.kidslock.app.prefs.PrefKeys.PIN_SALT
import com.kidslock.app.prefs.PrefKeys.PARENT_PIN
import com.kidslock.app.prefs.PrefKeys.MAX_PIN_FAILS
import com.kidslock.app.prefs.PrefKeys.PIN_LOCKOUT_MS
import java.math.BigInteger
import java.security.SecureRandom

/** 家长 PIN：PBKDF2 哈希、旧明文升级、错 5 次锁 30 秒。 */
class PinStore(private val prefs: SharedPreferences) : PinSettings {

    override fun verifyPin(pin: String?): Boolean {
        if (pin.isNullOrEmpty()) return false
        val stored = prefs.getString(PARENT_PIN, null)
        if (stored == null) return pin == DEFAULT_PARENT_PIN
        if (stored.length == 4 && stored.matches(Regex("\\d{4}"))) {
            if (stored == pin) {
                try {
                    setParentPin(pin)
                    resetPinFailures()
                } catch (e: RuntimeException) {
                    recordPinFailure()
                }
                return true
            }
            return false
        }
        if (stored.length == 64) {
            if (stored == PinHasher.sha256Hex(getOrCreateSalt() + "|" + pin)) {
                try {
                    setParentPin(pin)
                    resetPinFailures()
                } catch (e: RuntimeException) {
                    recordPinFailure()
                }
                return true
            }
            return false
        }
        if (stored.length == 96) {
            val saltHex = stored.substring(0, 32)
            val hashHex = stored.substring(32)
            return hashHex == PinHasher.pbkdf2Hex(pin, PinHasher.hexToBytes(saltHex))
        }
        return false
    }

    override fun setParentPin(pin: String?): Boolean {
        if (pin.isNullOrEmpty()) return false
        val salt = PinHasher.newSalt()
        val saltHex = PinHasher.bytesToHex(salt)
        val hashHex = PinHasher.pbkdf2Hex(pin, salt)
        if (hashHex.length != 64) {
            throw IllegalStateException("pbkdf2Hex 返回了非 64 位摘要")
        }
        prefs.edit()
            .putString(PARENT_PIN, saltHex + hashHex)
            .putBoolean(PIN_MODIFIED, true)
            .apply()
        return true
    }

    override fun isPinModified(): Boolean = prefs.getBoolean(PIN_MODIFIED, false)

    override fun shouldForcePinChange(): Boolean {
        if (isPinModified()) return false
        val stored = prefs.getString(PARENT_PIN, null)
        return stored == null || (stored.length == 4 && stored.matches(Regex("\\d{4}")))
    }

    override fun isPinUsable(): Boolean {
        val stored = prefs.getString(PARENT_PIN, null)
        if (stored == null) return true
        if (stored.length == 4 && stored.matches(Regex("\\d{4}"))) return true
        return stored.length == 64 || stored.length == 96
    }

    private fun getOrCreateSalt(): String {
        var salt: String = prefs.getString(PIN_SALT, null)
            ?: BigInteger(64, SecureRandom()).toString(16)
        prefs.edit().putString(PIN_SALT, salt).apply()
        return salt
    }

    override fun isPinLocked(): Boolean =
        System.currentTimeMillis() < prefs.getLong(PIN_LOCK_UNTIL, 0)

    override fun getPinLockRemainingMillis(): Long {
        val until = prefs.getLong(PIN_LOCK_UNTIL, 0)
        return maxOf(0L, until - System.currentTimeMillis())
    }

    override fun recordPinFailure() {
        val fails = prefs.getInt(PIN_FAIL_COUNT, 0) + 1
        if (fails >= MAX_PIN_FAILS) {
            prefs.edit()
                .putInt(PIN_FAIL_COUNT, 0)
                .putLong(PIN_LOCK_UNTIL, System.currentTimeMillis() + PIN_LOCKOUT_MS)
                .apply()
        } else {
            prefs.edit().putInt(PIN_FAIL_COUNT, fails).apply()
        }
    }

    override fun resetPinFailures() {
        prefs.edit().putInt(PIN_FAIL_COUNT, 0).putLong(PIN_LOCK_UNTIL, 0).apply()
    }
}
