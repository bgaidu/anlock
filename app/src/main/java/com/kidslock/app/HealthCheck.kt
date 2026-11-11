package com.kidslock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 管控体检：把「锁不住」相关的开关集中成一张可点击的清单。
 * 只负责状态展示与跳转，不修改系统设置。
 */
object HealthCheck {
    const val ID_OVERLAY = 1
    const val ID_USAGE = 2
    const val ID_ACCESSIBILITY = 3
    const val ID_BATTERY = 4
    const val ID_NOTIFICATIONS = 5
    const val ID_EXACT_ALARM = 6
    const val ID_DEVICE_OWNER = 7
    const val ID_ADB = 8
    const val ID_DEVELOPER = 9
    const val ID_MULTI_USER = 10

    data class Item(
        val id: Int,
        val title: String,
        val ok: Boolean,
        val state: String
    )

    fun items(context: Context): List<Item> {
        val owner = KioskManager.isDeviceOwner(context)
        val adb = PermissionStatus.adbEnabled(context)
        val dev = PermissionStatus.developerOptions(context)
        val multiUser = PermissionStatus.multipleUsers(context)
        return listOf(
            Item(ID_OVERLAY, "悬浮窗（锁屏兜底）", PermissionStatus.overlay(context), state(PermissionStatus.overlay(context))),
            Item(ID_ACCESSIBILITY, "无障碍守护（防切走）", PermissionStatus.accessibility(context), state(PermissionStatus.accessibility(context))),
            Item(ID_USAGE, "使用情况访问（补扣时长）", PermissionStatus.usageAccess(context), state(PermissionStatus.usageAccess(context))),
            Item(ID_BATTERY, "电池优化白名单", PermissionStatus.battery(context), state(PermissionStatus.battery(context))),
            Item(ID_NOTIFICATIONS, "通知权限（前台服务）", PermissionStatus.notifications(context), state(PermissionStatus.notifications(context))),
            Item(ID_EXACT_ALARM, "精确闹钟（准点锁屏）", PermissionStatus.exactAlarm(context), state(PermissionStatus.exactAlarm(context))),
            Item(ID_DEVICE_OWNER, "设备所有者（防卸载/锁定任务）", owner, if (owner) "已设置" else "未设置（可选，需 adb）"),
            Item(ID_ADB, "USB 调试（adb 可绕过）", !adb, if (adb) "已打开，建议关闭" else "已关闭"),
            Item(ID_DEVELOPER, "开发者选项", !dev, if (dev) "已打开，建议关闭" else "已关闭"),
            Item(ID_MULTI_USER, "多用户 / 访客模式", !multiUser, if (multiUser) "存在多个用户，其他用户不受管控" else "未启用")
        )
    }

    private fun state(ok: Boolean): String = if (ok) "已就绪" else "未开启"

    /** 打开对应系统设置页；返回 false 表示需要应用内提示（设备所有者）。 */
    fun launch(context: Context, id: Int): Boolean {
        if (id == ID_DEVICE_OWNER) return false
        val intent = when (id) {
            ID_OVERLAY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            } else null
            ID_USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            ID_ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ID_BATTERY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            } else null
            ID_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            ID_EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else null
            ID_ADB, ID_DEVELOPER -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            ID_MULTI_USER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent("android.settings.USER_SETTINGS")
            } else null
            else -> null
        } ?: return true
        return try {
            // 家长正在操作设置：给 2 分钟放行，避免被自己的"拦截系统设置"踢回桌面
            PrefManager(context).allowSettingsTemporarily()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w("HealthCheck", "launch failed for id=$id", e)
            true
        }
    }
}
