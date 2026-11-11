package com.kidslock.app.prefs

import android.content.SharedPreferences
import android.os.SystemClock
import com.kidslock.app.prefs.PrefKeys.AUTO_START
import com.kidslock.app.prefs.PrefKeys.DAILY_USED_MS
import com.kidslock.app.prefs.PrefKeys.DEFAULT_SHOW_COUNTDOWN
import com.kidslock.app.prefs.PrefKeys.EMERGENCY_REBOOT_UNLOCK
import com.kidslock.app.prefs.PrefKeys.EYE_REST_ENABLED
import com.kidslock.app.prefs.PrefKeys.EYE_REST_INTERVAL_MIN
import com.kidslock.app.prefs.PrefKeys.EYE_REST_LAST_MS
import com.kidslock.app.prefs.PrefKeys.LAN_ENABLED
import com.kidslock.app.prefs.PrefKeys.LAN_TOKEN
import com.kidslock.app.prefs.PrefKeys.LAST_BOOT_TIME
import com.kidslock.app.prefs.PrefKeys.REBOOT_COUNT
import com.kidslock.app.prefs.PrefKeys.SETUP_WIZARD_DONE
import com.kidslock.app.prefs.PrefKeys.SETTINGS_ALLOW_UNTIL
import com.kidslock.app.prefs.PrefKeys.SHOW_COUNTDOWN
import com.kidslock.app.prefs.PrefKeys.STAT_TOTAL
import com.kidslock.app.prefs.PrefKeys.WEEKLY_REPORT
import com.kidslock.app.prefs.PrefKeys.LAN_TOKEN_ALPHABET
import com.kidslock.app.prefs.PrefKeys.LAN_TOKEN_LENGTH
import java.security.SecureRandom

/** 应用级开关：自启动、角标、周报、护眼、向导、局域网面板、临时设置放行。 */
class AppOptionsStore(private val prefs: SharedPreferences) : AppOptionSettings {

    override fun isAutoStart(): Boolean = prefs.getBoolean(AUTO_START, true)

    override fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(AUTO_START, enabled).apply()
    }

    override fun isEmergencyRebootUnlock(): Boolean =
        prefs.getBoolean(EMERGENCY_REBOOT_UNLOCK, false)

    override fun setEmergencyRebootUnlock(enabled: Boolean) {
        prefs.edit().putBoolean(EMERGENCY_REBOOT_UNLOCK, enabled).apply()
    }

    override fun isShowCountdown(): Boolean =
        prefs.getBoolean(SHOW_COUNTDOWN, DEFAULT_SHOW_COUNTDOWN)

    override fun setShowCountdown(enabled: Boolean) {
        prefs.edit().putBoolean(SHOW_COUNTDOWN, enabled).apply()
    }

    override fun isWeeklyReportEnabled(): Boolean = prefs.getBoolean(WEEKLY_REPORT, true)

    override fun setWeeklyReportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(WEEKLY_REPORT, enabled).apply()
    }

    override fun isEyeRestEnabled(): Boolean = prefs.getBoolean(EYE_REST_ENABLED, false)

    override fun setEyeRestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(EYE_REST_ENABLED, enabled).apply()
        if (enabled) markEyeRestShown()
    }

    override fun getEyeRestIntervalMinutes(): Int =
        prefs.getInt(EYE_REST_INTERVAL_MIN, 20).coerceIn(5, 60)

    override fun setEyeRestIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(EYE_REST_INTERVAL_MIN, minutes.coerceIn(5, 60)).apply()
        markEyeRestShown()
    }

    override fun eyeRestLastMs(): Long = prefs.getLong(EYE_REST_LAST_MS, 0L)

    override fun markEyeRestShown() {
        prefs.edit().putLong(EYE_REST_LAST_MS, System.currentTimeMillis()).apply()
    }

    override fun isSetupWizardDone(): Boolean {
        if (prefs.contains(SETUP_WIZARD_DONE)) {
            return prefs.getBoolean(SETUP_WIZARD_DONE, false)
        }
        return prefs.getInt(STAT_TOTAL, 0) > 0 || prefs.getLong(DAILY_USED_MS, 0) > 0
    }

    override fun setSetupWizardDone(done: Boolean) {
        prefs.edit().putBoolean(SETUP_WIZARD_DONE, done).apply()
    }

    override fun isLanEnabled(): Boolean = prefs.getBoolean(LAN_ENABLED, false)

    override fun setLanEnabled(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(LAN_ENABLED, enabled)
        if (enabled && prefs.getString(LAN_TOKEN, null).isNullOrEmpty()) {
            editor.putString(LAN_TOKEN, generateLanToken())
        }
        editor.apply()
    }

    override fun getLanToken(): String {
        val existing = prefs.getString(LAN_TOKEN, null)
        if (!existing.isNullOrEmpty()) return existing
        val token = generateLanToken()
        prefs.edit().putString(LAN_TOKEN, token).apply()
        return token
    }

    override fun regenerateLanToken(): String {
        val token = generateLanToken()
        prefs.edit().putString(LAN_TOKEN, token).apply()
        return token
    }

    private fun generateLanToken(): String {
        val random = SecureRandom()
        val sb = StringBuilder(LAN_TOKEN_LENGTH)
        repeat(LAN_TOKEN_LENGTH) {
            sb.append(LAN_TOKEN_ALPHABET[random.nextInt(LAN_TOKEN_ALPHABET.length)])
        }
        return sb.toString()
    }

    override fun getLastBootTime(): Long = prefs.getLong(LAST_BOOT_TIME, 0)

    override fun setLastBootTime(time: Long) {
        prefs.edit().putLong(LAST_BOOT_TIME, time).apply()
    }

    override fun getRebootCount(): Int = prefs.getInt(REBOOT_COUNT, 0)

    override fun setRebootCount(count: Int) {
        prefs.edit().putInt(REBOOT_COUNT, count).apply()
    }

    override fun allowSettingsTemporarily(durationMs: Long) {
        prefs.edit()
            .putLong(SETTINGS_ALLOW_UNTIL, SystemClock.elapsedRealtime() + durationMs)
            .apply()
    }

    override fun isSettingsAllowedNow(): Boolean {
        val until = prefs.getLong(SETTINGS_ALLOW_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
    }
}
