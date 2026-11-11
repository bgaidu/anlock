package com.kidslock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 使用时段的边界闹钟：在下一个"开始/结束"时刻把服务叫醒。
 * 服务本身每 5 秒轮询，但厂商省电可能冻结进程；闹钟是兜底。
 */
object ScheduleAlarm {
    private const val TAG = "ScheduleAlarm"
    private const val REQUEST_CODE = 7301
    private const val ACTION_SCHEDULE_CHECK = "com.kidslock.app.ACTION_SCHEDULE_CHECK"

    fun schedule(context: Context) {
        val pref = PrefManager(context)
        if (!pref.isScheduleEnabled()) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = ScheduleMath.nextBoundaryMillis(System.currentTimeMillis()) { day ->
            pref.getScheduleStart(day) to pref.getScheduleEnd(day)
        }
        if (next == null) {
            cancel(context)
            return
        }
        val pi = pendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, next, pi)
            }
            Log.i(TAG, "next boundary in ${(next - System.currentTimeMillis()) / 1000}s")
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, next, pi)
            }
            Log.w(TAG, "exact alarm denied, using inexact", e)
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(pendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_SCHEDULE_CHECK)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
