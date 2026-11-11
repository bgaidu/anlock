package com.kidslock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * 服务复活：JobScheduler（可跨重启保留）+ AlarmManager（不精确闹钟）双通道。
 *
 * 为什么需要两条：
 * - 部分国产固件会拦截第三方应用的 BOOT_COMPLETED，但系统持久化作业仍会被重新排程；
 * - 另一些固件在省电策略里直接掐掉 JobScheduler，却仍放行 AlarmManager 唤醒。
 * 两条都失败时还有无障碍服务兜底（系统会重新绑定）。
 *
 * 只在「计时中 / 锁屏中」时排程，停止计时后取消，避免无意义的定时唤醒耗电。
 */
object Revival {

    private const val TAG = "Revival"
    const val REVIVE_ACTION = "com.kidslock.app.REVIVE"

    private const val JOB_ID = 7201
    private const val ALARM_REQUEST = 7202
    private const val PERIOD_MS = 15 * 60 * 1000L
    private const val FIRST_DELAY_MS = 5 * 60 * 1000L

    fun schedule(context: Context) {
        scheduleJob(context)
        scheduleAlarm(context)
    }

    fun cancel(context: Context) {
        try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            js?.cancel(JOB_ID)
        } catch (e: Exception) {
            Log.w(TAG, "cancel job failed", e)
        }
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(revivePendingIntent(context))
        } catch (e: Exception) {
            Log.w(TAG, "cancel alarm failed", e)
        }
    }

    private fun scheduleJob(context: Context) {
        try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            // getAllPendingJobs 是 API 24+ 才有的方法；低版本直接重复 schedule（同 id 会替换）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                js.allPendingJobs.any { it.id == JOB_ID }
            ) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJobService::class.java))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build()
            js.schedule(info)
            Log.i(TAG, "watchdog job scheduled")
        } catch (e: Exception) {
            Log.w(TAG, "schedule job failed", e)
        }
    }

    private fun scheduleAlarm(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + FIRST_DELAY_MS,
                PERIOD_MS,
                revivePendingIntent(context)
            )
        } catch (e: Exception) {
            Log.w(TAG, "schedule alarm failed", e)
        }
    }

    private fun revivePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReviveReceiver::class.java).setAction(REVIVE_ACTION)
        return PendingIntent.getBroadcast(
            context, ALARM_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 复活时按当前状态恢复：计时中 / 使用时段守护中拉起服务，锁屏中额外把锁屏页拉回前台。 */
    fun ensureRunning(context: Context, source: String) {
        val pref = PrefManager(context)
        val locked = pref.isLocked()
        val timerActive = pref.isTimerActive()
        val scheduleOn = pref.isScheduleEnabled()
        if (!locked && !timerActive && !scheduleOn) {
            cancel(context)
            return
        }
        RecoveryAccessibilityService.trySelfEnable(context)
        try {
            val intent = Intent(context, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "service revived via $source (locked=$locked timer=$timerActive schedule=$scheduleOn)")
            Diag.i(
                "Revival",
                "service revived via $source (locked=$locked timer=$timerActive schedule=$scheduleOn)"
            )
        } catch (e: Exception) {
            Log.w(TAG, "revive service failed via $source", e)
            Diag.w("Revival", "revive service failed via $source", e)
        }
        if (locked && !LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
            try {
                context.startActivity(
                    Intent(context, LockScreenActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                Log.w(TAG, "revive lock screen failed via $source", e)
            }
        }
    }
}
