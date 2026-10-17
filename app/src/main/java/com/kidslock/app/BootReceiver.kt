package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val EMERGENCY_WINDOW_MS = 5 * 60 * 1000L
        private const val FAST_REBOOT_THRESHOLD_MS = 3 * 60 * 1000L
        private const val FAST_REBOOT_COUNT_THRESHOLD = 3
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Boot received: $action")
        if (action == null) return

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
                || action == "android.intent.action.QUICKBOOT_POWERON"
                || action == "com.android.intent.action.BOOT_COMPLETED"
                || action == "android.intent.action.autoStart"
        if (!isBoot) return

        val pref = PrefManager(context)
        pref.deductOfflineIfAny()

        if (pref.isLocked()) {
            // 紧急解锁模式 1：检测紧急文件
            val emergencyFile = java.io.File("/sdcard/kidslock_emergency_unlock")
            if (emergencyFile.exists()) {
                Log.i(TAG, "Emergency unlock triggered, skipping lock screen")
                pref.setLocked(false)
                pref.setHomeAliasEnabled(context, false)
                pref.stopTimer()
                emergencyFile.delete()
                return
            }

            // 紧急解锁模式 2：检测快速重启（3 分钟内连续重启 3 次）
            val bootTime = System.currentTimeMillis()
            val lastBootTime = pref.getLastBootTime()
            val rebootCount = pref.getRebootCount()
            if (lastBootTime > 0 && bootTime - lastBootTime < FAST_REBOOT_THRESHOLD_MS) {
                val newCount = rebootCount + 1
                pref.setRebootCount(newCount)
                pref.setLastBootTime(bootTime)
                if (newCount >= FAST_REBOOT_COUNT_THRESHOLD) {
                    Log.i(TAG, "Fast reboot detected ($newCount times), skipping lock screen")
                    pref.setLocked(false)
                    pref.setHomeAliasEnabled(context, false)
                    pref.stopTimer()
                    pref.setRebootCount(0)
                    return
                }
            } else {
                pref.setRebootCount(1)
                pref.setLastBootTime(bootTime)
            }

            // 紧急模式：开机后 5 分钟内不恢复锁屏
            val lastLockTime = pref.getLastLockTime()
            if (lastLockTime > 0 && bootTime - lastLockTime > EMERGENCY_WINDOW_MS) {
                Log.i(TAG, "Emergency window active, skipping lock screen for 5 minutes")
                pref.setLocked(false)
                pref.setHomeAliasEnabled(context, false)
                pref.stopTimer()
                return
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
