package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TIMER_CHECK = "com.kidslock.app.ACTION_TIMER_CHECK"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_TIMER_CHECK) return
        val pref = PrefManager(context)
        if (!pref.isTimerActive()) {
            Log.i(TAG, "Timer not active, ignore expiry alarm")
            return
        }
        Log.i(TAG, "Expiry alarm fired, restarting guard service")
        RecoveryAccessibilityService.trySelfEnable(context)
        try {
            val service = Intent(context, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (e: Exception) {
            Log.e(TAG, "start service from alarm failed", e)
        }
    }
}
