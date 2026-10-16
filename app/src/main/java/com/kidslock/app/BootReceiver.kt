package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
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
