package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kidslock.app.question.ReviewStore

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
        AlertDialog.Builder(activity)
            .setTitle("近 7 天报告")
            .setView(container)
            .setPositiveButton("关闭", null)
            .setNeutralButton(
                if (pref.isWeeklyReportEnabled()) "每周提醒：已开启" else "每周提醒：已关闭"
            ) { _, _ -> toggle(activity, pref, onChange) }
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
