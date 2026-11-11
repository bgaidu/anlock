package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val FAST_REBOOT_THRESHOLD_MS = 3 * 60 * 1000L
        private const val FAST_REBOOT_COUNT_THRESHOLD = 3
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot received: $action")
        Diag.i("Boot", "received action=$action")
        if (action == null) return

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
                || action == "android.intent.action.QUICKBOOT_POWERON"
                || action == "com.android.intent.action.BOOT_COMPLETED"
                || action == "android.intent.action.autoStart"
        if (!isBoot) return

        // 先排上复活通道：即使下面的自启逻辑因权限/厂商限制失败，服务也能被拉回来
        Revival.schedule(context)

        val pref = PrefManager(context)
        pref.deductOfflineIfAny()
        KioskManager.applyOwnerPolicies(context)
        KioskManager.syncLockState(context)

        if (pref.isLocked()) {
            // 紧急解锁模式 1：紧急文件（adb 或老设备文件管理器写入应用外部私有目录，
            // 应用自身免存储权限即可读取；见 README「紧急解锁」）
            val emergencyDir = context.getExternalFilesDir(null)
            if (emergencyDir != null) {
                val emergencyFile = java.io.File(emergencyDir, "kidslock_emergency_unlock")
                if (emergencyFile.exists()) {
                    Log.i(TAG, "Emergency unlock triggered, skipping lock screen")
                    pref.setLocked(false)
                    pref.setHomeAliasEnabled(context, false)
                    pref.stopTimer()
                    emergencyFile.delete()
                    return
                }
            }

            // 紧急解锁模式 2：快速重启（3 分钟内连续重启 3 次），受设置页开关控制（默认关闭）
            val bootTime = System.currentTimeMillis()
            val lastBootTime = pref.getLastBootTime()
            val rebootCount = pref.getRebootCount()
            if (lastBootTime > 0 && bootTime - lastBootTime < FAST_REBOOT_THRESHOLD_MS) {
                val newCount = rebootCount + 1
                pref.setRebootCount(newCount)
                pref.setLastBootTime(bootTime)
                if (newCount >= FAST_REBOOT_COUNT_THRESHOLD) {
                    pref.setRebootCount(0)
                    if (pref.isEmergencyRebootUnlock()) {
                        Log.i(TAG, "Fast reboot detected ($newCount times), skipping lock screen")
                        pref.setLocked(false)
                        pref.setHomeAliasEnabled(context, false)
                        pref.stopTimer()
                        return
                    }
                }
            } else {
                pref.setRebootCount(1)
                pref.setLastBootTime(bootTime)
            }

            Log.i(TAG, "Device was locked, showing lock screen")
            pref.setHomeAliasEnabled(context, true)
            startLockService(context)
            val lockIntent = Intent(context, LockScreenActivity::class.java)
            lockIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            context.startActivity(lockIntent)
            return
        }

        if (pref.isTimerActive()) {
            if (pref.isTimerExpired()) {
                Log.i(TAG, "Timer expired during boot, locking")
                pref.setLocked(true)
                pref.setHomeAliasEnabled(context, true)
                pref.stopTimer()
                startLockService(context)
                val lockIntent = Intent(context, LockScreenActivity::class.java)
                lockIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                context.startActivity(lockIntent)
            } else {
                Log.i(TAG, "Timer still running, resuming service")
                startLockService(context)
            }
            return
        }

        if (pref.isAutoStart()) {
            Log.i(TAG, "Auto start enabled, starting timer")
            val minutes = pref.getWatchLimitMinutes()
            pref.startTimer(minutes)
            startLockService(context)
        }

        if (pref.isScheduleEnabled()) {
            // 使用时段是常驻守护：即使不开机自启计时，也要把服务拉起来按时段锁屏
            Log.i(TAG, "Schedule guard enabled, starting service")
            startLockService(context)
        }
    }

    private fun startLockService(context: Context) {
        val serviceIntent = Intent(context, LockService::class.java)
        serviceIntent.putExtra("action", "start_timer")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
