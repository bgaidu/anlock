package com.kidslock.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 按应用统计「今天用了多久」。
 *
 * 数据源是系统 UsageStats（家长授予「使用情况访问」后可用），本对象只额外维护
 * 当前前台会话的增量：进入应用时记下当时的系统累计值，前台期间用单调时钟补足
 * UsageStats 可能存在的统计延迟，切走/熄屏/跨天后自动重新对齐。
 */
object AppUsageTracker {
    private const val SELF_PACKAGE = "com.kidslock.app"
    private const val RAW_REFRESH_MS = 30_000L

    private var currentPackage: String? = null
    private var sessionDay: String = ""
    private var sessionBaseMs: Long = 0
    private var sessionStartElapsed: Long = 0

    private var rawCachePackage: String? = null
    private var rawCacheDay: String = ""
    private var rawCacheAtElapsed: Long = 0
    private var rawCacheValue: Long = 0

    fun currentPackage(): String? = currentPackage

    @Synchronized
    fun onForeground(context: Context, packageName: String) {
        if (packageName == SELF_PACKAGE || packageName == "android") {
            onBackground()
            return
        }
        val day = todayKey()
        if (packageName == currentPackage && day == sessionDay) return
        currentPackage = packageName
        sessionDay = day
        sessionBaseMs = cachedRawMsToday(context, packageName)
        sessionStartElapsed = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun onBackground() {
        currentPackage = null
        sessionDay = ""
        sessionBaseMs = 0
        sessionStartElapsed = 0
    }

    /** 今日累计前台时长；UsageStats 延迟时用当前会话的单调时钟补齐。 */
    @Synchronized
    fun usedMsToday(context: Context, packageName: String): Long {
        val raw = cachedRawMsToday(context, packageName)
        if (packageName != currentPackage) return raw

        // 熄屏期间不计时：每次检查都把会话起点对齐到当前值，避免把睡眠时间算进去
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) {
            sessionBaseMs = raw
            sessionStartElapsed = SystemClock.elapsedRealtime()
            return raw
        }

        val day = todayKey()
        if (day != sessionDay) {
            sessionDay = day
            sessionBaseMs = raw
            sessionStartElapsed = SystemClock.elapsedRealtime()
            return raw
        }

        val delta = (SystemClock.elapsedRealtime() - sessionStartElapsed).coerceAtLeast(0)
        return maxOf(raw, sessionBaseMs + delta)
    }

    /** 带短缓存的原始统计，避免 5 秒一次的守护轮询反复做全量查询。 */
    private fun cachedRawMsToday(context: Context, packageName: String): Long {
        val now = SystemClock.elapsedRealtime()
        val day = todayKey()
        if (packageName == rawCachePackage && day == rawCacheDay &&
            now - rawCacheAtElapsed < RAW_REFRESH_MS) {
            return rawCacheValue
        }
        val raw = rawUsedMsToday(context, packageName)
        rawCachePackage = packageName
        rawCacheDay = day
        rawCacheAtElapsed = now
        rawCacheValue = raw
        return raw
    }

    fun rawUsedMsToday(context: Context, packageName: String): Long {
        if (!PermissionStatus.usageAccess(context)) return 0
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0
        val start = startOfDayMillis()
        val end = System.currentTimeMillis()
        return try {
            val stats = usm.queryAndAggregateUsageStats(start, end) ?: return 0
            stats[packageName]?.totalTimeInForeground?.coerceAtLeast(0) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** 一次性取回今天所有应用的前台时长，供设置页应用列表使用。 */
    fun todayUsageMap(context: Context): Map<String, Long> {
        if (!PermissionStatus.usageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        return try {
            val stats = usm.queryAndAggregateUsageStats(
                startOfDayMillis(),
                System.currentTimeMillis()
            ) ?: return emptyMap()
            stats.entries.associate { (pkg, s) ->
                pkg to (s?.totalTimeInForeground ?: 0L).coerceAtLeast(0L)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
