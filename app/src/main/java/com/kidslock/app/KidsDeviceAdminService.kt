package com.kidslock.app

import android.app.admin.DeviceAdminService
import android.content.Intent
import android.util.Log

/**
 * 设备所有者被系统绑定的服务。
 *
 * 系统在开机后会主动绑定已激活 admin 的 DeviceAdminService（不受普通自启动拦截影响），
 * 这里作为 force-stop 之后的一条额外复活通道：补扣离线时间并按当前状态拉起守护服务。
 */
class KidsDeviceAdminService : DeviceAdminService() {

    companion object {
        private const val TAG = "KidsDeviceAdmin"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DeviceAdminService created")
        KioskManager.applyOwnerPolicies(this)
        KioskManager.syncLockState(this)

        val pref = PrefManager(this)
        pref.deductOfflineIfAny()
        if (pref.isLocked() || pref.isTimerActive()) {
            val intent = Intent(this, LockService::class.java)
            intent.putExtra("action", "start_timer")
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "start LockService from DeviceAdminService failed", e)
            }
        }
    }
}
