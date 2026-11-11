package com.kidslock.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionStatus {

    fun overlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun notifications(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        if (!runtimeGranted) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.areNotificationsEnabled() ?: true
    }

    fun exactAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    fun usageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun accessibility(context: Context): Boolean =
        RecoveryAccessibilityService.isServiceEnabled(context)

    fun battery(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun secureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED

    // ===== 防绕过检测 =====
    // 这几项不是「权限」，而是孩子可能用来绕过管控的系统开关，单独计数并在体检里提示。

    /** USB 调试：可用 adb 强停/卸载/改设置，是最直接的绕过通道。 */
    fun adbEnabled(context: Context): Boolean = readGlobalFlag(context, Settings.Global.ADB_ENABLED)

    /** 开发者选项：即使没插线，也说明设备被人动过，通常伴随 USB 调试。 */
    fun developerOptions(context: Context): Boolean =
        readGlobalFlag(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)

    /** 多用户 / 访客模式：孩子切换到另一个用户后，本应用看不到也管不到。 */
    fun multipleUsers(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val um = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            (um?.userCount ?: 1) > 1
        } catch (e: Exception) {
            false
        }
    }

    /** 当前存在的绕过风险项数量（0 表示没发现）。 */
    fun bypassRiskCount(context: Context): Int {
        var count = 0
        if (adbEnabled(context)) count++
        if (developerOptions(context)) count++
        if (multipleUsers(context)) count++
        return count
    }

    private fun readGlobalFlag(context: Context, key: String): Boolean = try {
        Settings.Global.getInt(context.contentResolver, key, 0) == 1
    } catch (e: Exception) {
        false
    }

    fun missingCount(context: Context): Int {
        var count = 0
        if (!overlay(context)) count++
        if (!notifications(context)) count++
        if (!exactAlarm(context)) count++
        if (!usageAccess(context)) count++
        if (!accessibility(context)) count++
        if (!battery(context)) count++
        return count
    }
}
