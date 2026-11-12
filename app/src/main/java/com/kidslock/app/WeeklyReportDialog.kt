package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kidslock.app.question.ReviewStore
import com.kidslock.app.question.Subject

/** 近 7 天使用/答题报告，以及「每周日提醒」开关。 */
object WeeklyReportDialog {

    fun show(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val history = pref.getUsageHistory(7)
        val totalMs = history.sumOf { it.second }
        val maxDay = history.maxByOrNull { it.second }
        val density = activity.resources.displayMetrics.density
        val chart = WeeklyChartView(activity).apply {
            setData(history)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * density).toInt()
            )
        }
        val acc = pref.getAccuracyPercent()
        val summaryText = buildString {
            append("合计：").append(formatMinutes((totalMs + 59_999) / 60_000)).append('\n')
            append("日均：").append(formatMinutes((totalMs / 7 + 59_999) / 60_000)).append('\n')
            if (maxDay != null && maxDay.second > 0) {
                append("最多：").append(maxDay.first)
                    .append("（").append(formatMinutes((maxDay.second + 59_999) / 60_000)).append("）\n")
            }
            append('\n')
            if (acc < 0) {
                append("答题：暂无记录\n")
            } else {
                append("答题：").append(pref.getStatTotal()).append(" 题，正确率 ")
                    .append(acc).append("%\n")
            }
            append("星星：").append(pref.getStars()).append(" 颗 · 最高连对 ")
                .append(pref.getBestCombo()).append(" 题\n")
            append("错题待复习：").append(ReviewStore.dueCount(activity)).append(" 道")

            val study = pref.getStudyStats(7)
            val subjectStats = study.bySubject
                .filter { it.total > 0 }
                .sortedBy { Subject.ORDER.indexOf(it.subject).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            if (subjectStats.isNotEmpty()) {
                append("\n\n近 7 天分科：")
                for (stat in subjectStats) {
                    append("\n· ").append(Subject.label(stat.subject)).append("  ")
                        .append(stat.total).append(" 题 · 正确率 ")
                        .append(stat.accuracyPercent).append("%")
                }
            }
            if (study.goalEnabled) {
                append("\n每日目标：").append(study.goalQuestions).append(" 题 · 连续达标 ")
                    .append(study.streakDays).append(" 天")
            }
            val apps = AppUsageStats.top(activity, 7, 5)
            if (apps.isNotEmpty()) {
                append("\n\n使用最多的应用：\n")
                for ((index, entry) in apps.withIndex()) {
                    append(index + 1).append(". ").append(entry.label)
                        .append("  ").append(AppUsageStats.formatDuration(entry.millis))
                    if (index != apps.lastIndex) append('\n')
                }
            }
        }
        val summary = TextView(activity).apply {
            text = summaryText
            textSize = 14f
            setPadding(0, (12 * density).toInt(), 0, 0)
            setTextIsSelectable(true)
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), 0)
            addView(chart)
            addView(summary)
        }
        val shareText = buildString {
            append("儿童锁屏 · 近 7 天报告\n")
            append("————————————\n")
            append(summaryText)
        }
        AlertDialog.Builder(activity)
            .setTitle("近 7 天报告")
            .setView(container)
            .setPositiveButton("分享报告") { _, _ ->
                ShareUtil.shareText(activity, shareText, "儿童锁屏近 7 天报告", "分享周报")
            }
            .setNeutralButton(
                if (pref.isWeeklyReportEnabled()) "每周提醒：已开启" else "每周提醒：已关闭"
            ) { _, _ -> toggle(activity, pref, onChange) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun toggle(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val enabled = !pref.isWeeklyReportEnabled()
        pref.setWeeklyReportEnabled(enabled)
        if (enabled) {
            WeeklyReportAlarm.schedule(activity)
            Toast.makeText(activity, "已开启：每周日 20:00 提醒查看本周报告", Toast.LENGTH_SHORT).show()
        } else {
            WeeklyReportAlarm.cancel(activity)
            Toast.makeText(activity, "已关闭每周提醒", Toast.LENGTH_SHORT).show()
        }
        onChange()
    }

    private fun formatMinutes(minutes: Long): String =
        if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟"
}
