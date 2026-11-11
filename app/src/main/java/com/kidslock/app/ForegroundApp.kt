package com.kidslock.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 「孩子现在正在看什么」——供局域网家长面板展示。
 *
 * 只看最近 10 分钟的前台事件，取最新一条；跳过自己（面板本身会占用前台）。
 * 仅在设备本机的面板页面展示，不上传、不落盘；未授予「使用情况访问」时返回 null。
 */
object ForegroundApp {
    private const val LOOKBACK_MS = 10 * 60 * 1000L
    private const val SELF_PACKAGE = "com.kidslock.app"

    data class Info(val packageName: String, val label: String)

    fun current(context: Context): Info? {
        if (!PermissionStatus.usageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = try {
            usm.queryEvents(now - LOOKBACK_MS, now)
        } catch (e: Exception) {
            return null
        }
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastStamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!isForegroundEvent(event.eventType)) continue
            val pkg = event.packageName ?: continue
            if (pkg == SELF_PACKAGE || pkg == "android") continue
            if (event.timeStamp >= lastStamp) {
                lastStamp = event.timeStamp
                lastPackage = pkg
            }
        }
        val pkg = lastPackage ?: return null
        return Info(pkg, labelOf(context, pkg) ?: pkg)
    }

    private fun isForegroundEvent(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                type == UsageEvents.Event.ACTIVITY_RESUMED
    }

    private fun labelOf(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
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
}
