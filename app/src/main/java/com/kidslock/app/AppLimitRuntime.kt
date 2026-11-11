package com.kidslock.app

import android.content.Context
import com.kidslock.app.prefs.AppLimitPolicy
import java.util.Calendar

/**
 * 应用限额的运行时判断：把家长配置、周末规则、当日加时/重置偏移与 UsageStats 合到一起。
 */
object AppLimitRuntime {
    private const val SELF_PACKAGE = "com.kidslock.app"
    private val PROTECTED_PACKAGES = setOf("android", "com.android.systemui", SELF_PACKAGE)

    data class State(
        val packageName: String,
        val usedMs: Long,
        val limitMinutes: Int,
        val remainingMs: Long,
        val blocked: Boolean
    )

    fun state(
        context: Context,
        pref: PrefManager,
        packageName: String,
        now: Calendar = Calendar.getInstance()
    ): State? {
        if (!pref.isAppLimitEnabled()) return null
        if (packageName in PROTECTED_PACKAGES) return null
        val limit = pref.getAppLimit(packageName)
        if (!limit.configured) return null
        val base = AppLimitPolicy.resolveMinutes(
            limit.weekdayMinutes,
            limit.weekendMinutes,
            pref.isWeekend(now)
        )
        val effective = AppLimitPolicy.effectiveLimitMinutes(
            base,
            pref.getAppLimitBonusMinutes(packageName)
        )
        val raw = AppUsageTracker.usedMsToday(context, packageName)
        val offset = pref.getAppLimitUsageOffsetMs(packageName)
        val used = (raw - offset).coerceAtLeast(0L)
        return State(
            packageName = packageName,
            usedMs = used,
            limitMinutes = effective,
            remainingMs = AppLimitPolicy.remainingMs(effective, used),
            blocked = AppLimitPolicy.isExhausted(effective, used)
        )
    }
}
