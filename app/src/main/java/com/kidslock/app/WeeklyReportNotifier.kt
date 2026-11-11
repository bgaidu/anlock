package com.kidslock.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kidslock.app.question.ReviewStore

/** 每周报告通知：到点现算近 7 天数据，点开直接进「近 7 天报告」。 */
object WeeklyReportNotifier {
    private const val CHANNEL_ID = "kids_lock_weekly"
    private const val NOTIFICATION_ID = 1003

    fun show(context: Context) {
        val pref = PrefManager(context)
        if (!pref.isWeeklyReportEnabled()) return
        val history = pref.getUsageHistory(7)
        val totalMs = history.sumOf { it.second }
        val totalMinutes = (totalMs + 59_999) / 60_000
        val acc = pref.getAccuracyPercent()
        val due = ReviewStore.dueCount(context)

        val summary = buildString {
            append("本周合计 ").append(formatMinutes(totalMinutes))
            if (acc >= 0) append(" · 正确率 ").append(acc).append('%')
            append("\n星星 ").append(pref.getStars()).append(" 颗")
            if (due > 0) append(" · 错题待复习 ").append(due).append(" 道")
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "每周使用报告",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每周日 20:00 汇总本周使用时长与答题情况"
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_SHOW_WEEKLY_REPORT, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, 4001, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("本周屏幕使用报告")
            .setContentText(summary.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
            Diag.i("Report", "weekly notification posted")
        } catch (e: Exception) {
            Diag.w("Report", "post notification failed: ${e.message}")
        }
    }

    private fun formatMinutes(minutes: Long): String =
        if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟"
}
