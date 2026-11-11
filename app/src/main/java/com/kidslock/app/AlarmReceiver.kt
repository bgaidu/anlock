package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TIMER_CHECK = "com.kidslock.app.ACTION_TIMER_CHECK"
        const val ACTION_SCHEDULE_CHECK = "com.kidslock.app.ACTION_SCHEDULE_CHECK"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // 周报提醒：不依赖服务是否存活，到点直接发本地通知
        if (action == WeeklyReportAlarm.ACTION) {
            Log.i(TAG, "Weekly report alarm fired")
            WeeklyReportNotifier.show(context)
            return
        }

        if (action != ACTION_TIMER_CHECK && action != ACTION_SCHEDULE_CHECK) return
        val pref = PrefManager(context)

        if (action == ACTION_TIMER_CHECK && !pref.isTimerActive()) {
            Log.i(TAG, "Timer not active, ignore expiry alarm")
            return
        }
        if (action == ACTION_SCHEDULE_CHECK && !pref.isScheduleEnabled()) {
            Log.i(TAG, "Schedule disabled, ignore boundary alarm")
            return
        }

        Log.i(TAG, "Alarm fired ($action), restarting guard service")
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
