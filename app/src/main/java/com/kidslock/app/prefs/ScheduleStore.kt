package com.kidslock.app.prefs

import android.content.SharedPreferences
import android.os.SystemClock
import com.kidslock.app.ScheduleMath
import com.kidslock.app.prefs.PrefKeys.DEFAULT_SCHEDULE_END_MIN
import com.kidslock.app.prefs.PrefKeys.DEFAULT_SCHEDULE_START_MIN
import com.kidslock.app.prefs.PrefKeys.SCHEDULE_BYPASS_UNTIL
import com.kidslock.app.prefs.PrefKeys.SCHEDULE_ENABLED
import com.kidslock.app.prefs.PrefKeys.SCHEDULE_END_PREFIX
import com.kidslock.app.prefs.PrefKeys.SCHEDULE_FREE_MODE
import com.kidslock.app.prefs.PrefKeys.SCHEDULE_START_PREFIX
import java.util.Calendar

/** 使用时段：周一~周日独立窗口、跨零点、家长临时放行。 */
class ScheduleStore(private val prefs: SharedPreferences) : ScheduleSettings {

    override fun isScheduleEnabled(): Boolean = prefs.getBoolean(SCHEDULE_ENABLED, false)

    override fun setScheduleEnabled(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(SCHEDULE_ENABLED, enabled)
        if (enabled && !hasAnyScheduleWindow()) {
            // 第一次开启时给一个合理的默认时段，避免「开了但等于没开」
            for (day in 0..6) {
                editor.putInt(SCHEDULE_START_PREFIX + day, DEFAULT_SCHEDULE_START_MIN)
                editor.putInt(SCHEDULE_END_PREFIX + day, DEFAULT_SCHEDULE_END_MIN)
            }
        }
        editor.apply()
    }

    override fun isScheduleFreeMode(): Boolean =
        prefs.getBoolean(SCHEDULE_FREE_MODE, false)

    override fun setScheduleFreeMode(free: Boolean) {
        prefs.edit().putBoolean(SCHEDULE_FREE_MODE, free).apply()
    }

    override fun getScheduleStart(day: Int): Int {
        if (day !in 0..6) return -1
        return prefs.getInt(SCHEDULE_START_PREFIX + day, -1)
    }

    override fun getScheduleEnd(day: Int): Int {
        if (day !in 0..6) return -1
        return prefs.getInt(SCHEDULE_END_PREFIX + day, -1)
    }

    override fun setScheduleWindow(day: Int, startMin: Int, endMin: Int) {
        if (day !in 0..6) return
        val valid = startMin in 0..1439 && endMin in 0..1439 && startMin != endMin
        prefs.edit().apply {
            if (valid) {
                putInt(SCHEDULE_START_PREFIX + day, startMin)
                putInt(SCHEDULE_END_PREFIX + day, endMin)
            } else {
                remove(SCHEDULE_START_PREFIX + day)
                remove(SCHEDULE_END_PREFIX + day)
            }
        }.apply()
    }

    override fun clearScheduleDay(day: Int) {
        setScheduleWindow(day, -1, -1)
    }

    override fun scheduleDayIndex(now: Calendar): Int = ScheduleMath.dayIndex(now)

    override fun scheduleWindowLabel(day: Int): String {
        val start = getScheduleStart(day)
        val end = getScheduleEnd(day)
        if (start < 0 || end < 0) return "不限"
        return ScheduleMath.format(start) + "–" + ScheduleMath.format(end)
    }

    override fun isWithinSchedule(now: Calendar): Boolean {
        if (!isScheduleEnabled()) return true
        if (isScheduleBypassActive()) return true
        val day = scheduleDayIndex(now)
        val start = getScheduleStart(day)
        val end = getScheduleEnd(day)
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return ScheduleMath.isWithin(start, end, minutes)
    }

    override fun grantScheduleBypass(durationMs: Long) {
        prefs.edit()
            .putLong(SCHEDULE_BYPASS_UNTIL, SystemClock.elapsedRealtime() + durationMs)
            .apply()
    }

    override fun isScheduleBypassActive(): Boolean {
        val until = prefs.getLong(SCHEDULE_BYPASS_UNTIL, 0)
        return until > 0 && SystemClock.elapsedRealtime() < until
    }

    private fun hasAnyScheduleWindow(): Boolean {
        for (day in 0..6) {
            if (getScheduleStart(day) >= 0 && getScheduleEnd(day) >= 0) return true
        }
        return false
    }
}
