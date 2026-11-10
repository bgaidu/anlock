package com.kidslock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 设备管理器入口。
 *
 * 通过 adb 执行：
 *   adb shell dpm set-device-owner com.kidslock.app/.KidsDeviceAdminReceiver
 * 之后应用成为 Device Owner，可使用 LockTask（kiosk）、禁用系统锁屏、阻止卸载等能力。
 */
class KidsDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "KidsDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        KioskManager.applyOwnerPolicies(context)
        if (PrefManager(context).isLocked()) {
            KioskManager.syncLockState(context)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "关闭设备管理后，儿童锁屏将失去防强制停止与固定屏幕保护。"
    }
}
