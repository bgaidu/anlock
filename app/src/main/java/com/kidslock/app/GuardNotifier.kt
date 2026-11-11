package com.kidslock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 管控能力告警：当「计时中 / 锁屏中」却没有任何拦截能力（悬浮窗、无障碍、使用情况访问）
 * 时主动通知家长，避免出现「以为在管、其实早就失效」的静默失败。
 *
 * 只在状态切换时提示一次；能力恢复后自动撤掉通知，下次丢失会再提示。
 */
object GuardNotifier {

    private const val TAG = "GuardNotifier"
    private const val CHANNEL_ID = "kids_guard_alert"
    private const val NOTIF_ID = 1002
    private const val STATE_PREFS = "kids_guard_alerts"
    private const val KEY_WAS_OVERLAY = "overlay_was_granted"
    private const val KEY_NOTIFIED_NO_CAPABILITY = "notified_no_capability"
    private const val KEY_NOTIFIED_OVERLAY_LOST = "notified_overlay_lost"
    private const val KEY_NOTIFIED_ADB = "notified_adb_enabled"

    fun check(context: Context) {
        try {
            checkInternal(context)
        } catch (e: Exception) {
            Log.w(TAG, "capability check failed", e)
        }
    }

    private fun checkInternal(context: Context) {
        val pref = PrefManager(context)
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

        // 没在管控时不打扰；顺手清掉历史提示状态
        if (!pref.isTimerActive() && !pref.isLocked()) {
            clear(context)
            state.edit().clear().apply()
            return
        }

        val overlay = PermissionStatus.overlay(context)
        val usage = PermissionStatus.usageAccess(context)
        val accessibility = PermissionStatus.accessibility(context)
        val monitoring = overlay || usage || accessibility

        val wasOverlay = state.getBoolean(KEY_WAS_OVERLAY, false)
        if (overlay && !wasOverlay) {
            state.edit().putBoolean(KEY_WAS_OVERLAY, true).apply()
        }

        // USB 调试是最高危的绕过通道：可 adb 强停、卸载、改设置。开启期间每次进入管控只提示一次。
        if (PermissionStatus.adbEnabled(context)) {
            if (!state.getBoolean(KEY_NOTIFIED_ADB, false)) {
                state.edit().putBoolean(KEY_NOTIFIED_ADB, true).apply()
                notify(
                    context,
                    "USB 调试已开启",
                    "孩子可以通过 adb 强行停止或卸载儿童锁，建议在开发者选项里关闭 USB 调试"
                )
                Log.w(TAG, "adb enabled while monitoring")
            }
        } else {
            state.edit().putBoolean(KEY_NOTIFIED_ADB, false).apply()
        }

        if (monitoring) {
            // 能力恢复：撤掉「完全失效」通知，并允许下次再提示
            if (state.getBoolean(KEY_NOTIFIED_NO_CAPABILITY, false)) {
                cancel(context)
                state.edit().putBoolean(KEY_NOTIFIED_NO_CAPABILITY, false).apply()
            }

            // 悬浮窗被系统回收（常见于厂商固件升级 / 省电清理）：提示一次。
            // 此时无障碍/使用情况仍在，锁屏仍能拉起，但「盖住画面」的即时拦截没了。
            if (wasOverlay && !overlay) {
                if (!state.getBoolean(KEY_NOTIFIED_OVERLAY_LOST, false)) {
                    state.edit().putBoolean(KEY_NOTIFIED_OVERLAY_LOST, true).apply()
                    notify(
                        context,
                        "悬浮窗权限已被系统收回",
                        "孩子可能已绕过时间限制，点此重新授权悬浮窗"
                    )
                    Log.w(TAG, "overlay permission revoked while monitoring")
                }
            } else if (overlay) {
                state.edit().putBoolean(KEY_NOTIFIED_OVERLAY_LOST, false).apply()
            }
            return
        }

        if (state.getBoolean(KEY_NOTIFIED_NO_CAPABILITY, false)) return
        state.edit().putBoolean(KEY_NOTIFIED_NO_CAPABILITY, true).apply()
        notify(
            context,
            "使用时间管理还没生效",
            "缺少悬浮窗 / 无障碍 / 使用情况权限，计时和答题拦截都不会生效，点此开启"
        )
        Log.w(TAG, "no monitoring capability: overlay=$overlay usage=$usage accessibility=$accessibility")
    }

    fun clear(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    /** 设备管理器被停用：防卸载 / 固定屏幕能力失效，立即告知家长。 */
    fun notifyAdminDisabled(context: Context) {
        try {
            notify(
                context,
                "设备管理器已被关闭",
                "防卸载与固定屏幕保护失效，点此重新开启设备管理器"
            )
        } catch (e: Exception) {
            Log.w(TAG, "notify admin disabled failed", e)
        }
    }

    private fun cancel(context: Context) = clear(context)

    private fun notify(context: Context, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "管控状态提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val pi = PendingIntent.getActivity(
            context, 2,
            Intent(context, PermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(context, CHANNEL_ID)
        else
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        val notification = builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIF_ID, notification)
        Diag.w("Guard", "notified: $title / $text")
    }
}
