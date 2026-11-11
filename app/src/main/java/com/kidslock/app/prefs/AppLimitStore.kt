package com.kidslock.app.prefs

import android.content.SharedPreferences
import com.kidslock.app.prefs.PrefKeys.APP_LIMIT_BONUS_PREFIX
import com.kidslock.app.prefs.PrefKeys.APP_LIMIT_ENABLED
import com.kidslock.app.prefs.PrefKeys.APP_LIMIT_OFFSET_PREFIX
import com.kidslock.app.prefs.PrefKeys.APP_LIMIT_WEEKDAY_PREFIX
import com.kidslock.app.prefs.PrefKeys.APP_LIMIT_WEEKEND_PREFIX
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单个应用的限额配置（分钟；-1 不限，0 禁止）。 */
data class AppLimit(
    val packageName: String,
    val weekdayMinutes: Int,
    val weekendMinutes: Int
) {
    val configured: Boolean
        get() = weekdayMinutes >= 0 || weekendMinutes >= 0
}

/**
 * 应用级限额的持久化。
 *
 * 当日用量本身不落盘，统一以系统 UsageStats 为准；这里只保存家长的配置、
 * 当日临时加时与「重置今日用量」的偏移量。临时键带日期后缀，跨天自动失效。
 */
class AppLimitStore(private val prefs: SharedPreferences) : AppLimitSettings {

    override fun isAppLimitEnabled(): Boolean = prefs.getBoolean(APP_LIMIT_ENABLED, false)

    override fun setAppLimitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(APP_LIMIT_ENABLED, enabled).apply()
    }

    override fun getAppLimit(packageName: String): AppLimit = AppLimit(
        packageName,
        prefs.getInt(APP_LIMIT_WEEKDAY_PREFIX + packageName, AppLimitPolicy.UNLIMITED),
        prefs.getInt(APP_LIMIT_WEEKEND_PREFIX + packageName, AppLimitPolicy.UNLIMITED)
    )

    override fun setAppLimit(packageName: String, weekdayMinutes: Int, weekendMinutes: Int) {
        if (packageName.isEmpty()) return
        val weekday = weekdayMinutes.coerceIn(AppLimitPolicy.UNLIMITED, AppLimitPolicy.MAX_MINUTES)
        val weekend = weekendMinutes.coerceIn(AppLimitPolicy.UNLIMITED, AppLimitPolicy.MAX_MINUTES)
        val editor = prefs.edit()
        if (weekday >= 0) {
            editor.putInt(APP_LIMIT_WEEKDAY_PREFIX + packageName, weekday)
        } else {
            editor.remove(APP_LIMIT_WEEKDAY_PREFIX + packageName)
        }
        if (weekend >= 0) {
            editor.putInt(APP_LIMIT_WEEKEND_PREFIX + packageName, weekend)
        } else {
            editor.remove(APP_LIMIT_WEEKEND_PREFIX + packageName)
        }
        editor.apply()
    }

    override fun clearAppLimit(packageName: String) {
        prefs.edit()
            .remove(APP_LIMIT_WEEKDAY_PREFIX + packageName)
            .remove(APP_LIMIT_WEEKEND_PREFIX + packageName)
            .apply()
    }

    override fun configuredAppPackages(): Set<String> {
        val out = LinkedHashSet<String>()
        for (key in prefs.all.keys) {
            when {
                key.startsWith(APP_LIMIT_WEEKDAY_PREFIX) ->
                    out.add(key.removePrefix(APP_LIMIT_WEEKDAY_PREFIX))
                key.startsWith(APP_LIMIT_WEEKEND_PREFIX) ->
                    out.add(key.removePrefix(APP_LIMIT_WEEKEND_PREFIX))
            }
        }
        out.remove("")
        return out
    }

    override fun hasAppLimit(packageName: String): Boolean = getAppLimit(packageName).configured

    override fun getAppLimitBonusMinutes(packageName: String): Int =
        prefs.getInt(bonusKey(packageName), 0).coerceAtLeast(0)

    override fun addAppLimitBonusMinutes(packageName: String, minutes: Int) {
        if (minutes <= 0) return
        val next = (getAppLimitBonusMinutes(packageName) + minutes)
            .coerceAtMost(AppLimitPolicy.MAX_MINUTES)
        prefs.edit().putInt(bonusKey(packageName), next).apply()
    }

    override fun getAppLimitUsageOffsetMs(packageName: String): Long =
        prefs.getLong(offsetKey(packageName), 0L).coerceAtLeast(0L)

    override fun setAppLimitUsageOffsetMs(packageName: String, offsetMs: Long) {
        prefs.edit().putLong(offsetKey(packageName), offsetMs.coerceAtLeast(0L)).apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun bonusKey(packageName: String): String =
        APP_LIMIT_BONUS_PREFIX + packageName + "_" + todayKey()

    private fun offsetKey(packageName: String): String =
        APP_LIMIT_OFFSET_PREFIX + packageName + "_" + todayKey()
}
