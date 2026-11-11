package com.kidslock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * 每周日 20:00 的本地汇总提醒。
 *
 * 不依赖服务器：到点由 AlarmManager 唤醒 AlarmReceiver，现算近 7 天数据并发一条通知。
 * 使用非精确重复闹钟（每周一次），避免申请精确闹钟权限、也省电。
 */
object WeeklyReportAlarm {
    const val ACTION = "com.kidslock.app.ACTION_WEEKLY_REPORT"
    private const val TAG = "WeeklyReportAlarm"
    private const val REQUEST_CODE = 3001
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val HOUR = 20

    fun schedule(context: Context) {
        val pref = PrefManager(context)
        if (!pref.isWeeklyReportEnabled()) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val trigger = nextTriggerMillis()
        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger,
                WEEK_MS,
                pendingIntent(context)
            )
            Diag.i("Report", "weekly alarm scheduled at $trigger")
        } catch (e: Exception) {
            Log.w(TAG, "schedule weekly report failed", e)
            Diag.w("Report", "schedule failed: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(pendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "cancel weekly report failed", e)
        }
    }

    private fun nextTriggerMillis(): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysUntilSunday = (Calendar.SUNDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
