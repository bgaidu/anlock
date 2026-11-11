package com.kidslock.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 按应用汇总近 N 天前台使用时长，用于家长报告与局域网面板。
 *
 * 只读本机 UsageStats，不上传、不落盘；只展示有桌面入口的应用，
 * 过滤系统 UI / 输入法 / 设置之类的噪声。
 */
object AppUsageStats {
    private const val DAY_MS = 86_400_000L
    private const val SELF_PACKAGE = "com.kidslock.app"
    private const val MIN_VISIBLE_MS = 60_000L

    data class Entry(val packageName: String, val label: String, val millis: Long)

    fun top(context: Context, days: Int = 7, limit: Int = 8): List<Entry> {
        if (!PermissionStatus.usageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - days.coerceAtLeast(1) * DAY_MS
        val stats = try {
            usm.queryAndAggregateUsageStats(start, end)
        } catch (e: Exception) {
            return emptyList()
        } ?: return emptyList()

        val pm = context.packageManager
        val entries = ArrayList<Entry>()
        for ((pkg, usage) in stats) {
            if (pkg == SELF_PACKAGE || pkg == "android") continue
            val millis = usage.totalTimeInForeground
            if (millis < MIN_VISIBLE_MS) continue
            if (!hasLaunchEntry(pm, pkg)) continue
            entries.add(Entry(pkg, labelOf(pm, pkg) ?: pkg, millis))
        }
        return entries.sortedByDescending { it.millis }.take(limit)
    }

    private fun hasLaunchEntry(pm: PackageManager, pkg: String): Boolean =
        try {
            pm.getLaunchIntentForPackage(pkg) != null
        } catch (e: Exception) {
            false
        }

    private fun labelOf(pm: PackageManager, pkg: String): String? = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        null
    }

    /** 紧凑时长：45 分钟 / 2 小时 5 分钟 / 3 小时。 */
    fun formatDuration(millis: Long): String {
        val minutes = (millis + 59_999) / 60_000
        return if (minutes < 60) {
            "$minutes 分钟"
        } else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h 小时" else "$h 小时 $m 分钟"
        }
    }
}
