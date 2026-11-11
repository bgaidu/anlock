package com.kidslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * 设备所有者（Device Owner）+ LockTask（kiosk）封装。
 *
 * 未成为 Device Owner 时所有方法都会安静地跳过，应用保持原有行为。
 */
object KioskManager {

    private const val TAG = "KioskManager"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, KidsDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean {
        val d = dpm(context) ?: return false
        return try {
            d.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isAdminActive(context: Context): Boolean {
        val d = dpm(context) ?: return false
        return try {
            d.isAdminActive(adminComponent(context))
        } catch (e: Exception) {
            false
        }
    }

    /** 幂等：把本应用设为唯一允许 LockTask 的包，并禁止卸载。 */
    fun applyOwnerPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val d = dpm(context) ?: return
        val admin = adminComponent(context)
        try {
            d.setLockTaskPackages(admin, arrayOf(context.packageName))
        } catch (e: Exception) {
            Log.w(TAG, "setLockTaskPackages failed", e)
        }
        try {
            d.setUninstallBlocked(admin, context.packageName, true)
        } catch (e: Exception) {
            Log.w(TAG, "setUninstallBlocked failed", e)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // 固定屏幕期间隐藏 Home / 最近任务 / 通知栏 / 电源菜单
                d.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            } catch (e: Exception) {
                Log.w(TAG, "setLockTaskFeatures failed", e)
            }
        }
        try {
            // 设备所有者下禁止再新增用户/访客，堵住「切到其他用户绕过」的通道
            d.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        } catch (e: Exception) {
            Log.w(TAG, "DISALLOW_ADD_USER failed", e)
        }
    }

    /**
     * 根据当前锁定状态同步系统策略：
     * 锁定时禁用系统锁屏（避免系统密码/keyguard 盖在儿童锁屏之上），解锁后恢复。
     */
    fun syncLockState(context: Context) {
        if (!isDeviceOwner(context)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val d = dpm(context) ?: return
        val locked = PrefManager(context).isLocked()
        try {
            d.setKeyguardDisabled(adminComponent(context), locked)
        } catch (e: Exception) {
            Log.w(TAG, "setKeyguardDisabled failed", e)
        }
        try {
            // 锁定时禁用状态栏下拉，避免从通知栏进系统设置/切网络
            d.setStatusBarDisabled(adminComponent(context), locked)
        } catch (e: Exception) {
            Log.w(TAG, "setStatusBarDisabled failed", e)
        }
    }

    /** 进入 kiosk。必须在 Activity 前台时调用。重复调用是安全的。 */
    fun enterLockTask(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        if (isInLockTaskMode(activity)) return
        val d = dpm(activity) ?: return
        val admin = adminComponent(activity)
        applyOwnerPolicies(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                d.setKeyguardDisabled(admin, true)
            } catch (e: Exception) {
                Log.w(TAG, "setKeyguardDisabled(true) failed", e)
            }
        }
        try {
            if (!isInLockTaskMode(activity)) {
                activity.startLockTask()
                Log.i(TAG, "LockTask entered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startLockTask failed", e)
        }
    }

    /** 退出 kiosk，并恢复系统锁屏。 */
    fun exitLockTask(activity: Activity) {
        try {
            if (isInLockTaskMode(activity)) {
                activity.stopLockTask()
                Log.i(TAG, "LockTask exited")
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed", e)
        }
        if (isDeviceOwner(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val d = dpm(activity) ?: return
            try {
                d.setKeyguardDisabled(adminComponent(activity), false)
            } catch (e: Exception) {
                Log.w(TAG, "setKeyguardDisabled(false) failed", e)
            }
        }
    }

    fun isInLockTaskMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return try {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: Exception) {
            false
        }
    }
}
