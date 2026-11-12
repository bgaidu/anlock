package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.kidslock.app.prefs.StudyStatsPolicy
import com.kidslock.app.prefs.StudyStatsSummary
import com.kidslock.app.question.Subject

/**
 * 家长查看分科/近 7 天学习数据，并设置每日答题目标。
 * 只做激励展示，不参与解锁判定，也不会改变额度。
 */
object StudyStatsDialog {

    fun show(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val summary = pref.getStudyStats(7)
        val content = buildContent(summary)
        val textView = TextView(activity).apply {
            text = content
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(28, 20, 28, 20)
        }
        val scroll = ScrollView(activity).apply {
            addView(
                textView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        AlertDialog.Builder(activity)
            .setTitle("学习数据（近 7 天）")
            .setView(scroll)
            .setPositiveButton("每日目标") { _, _ ->
                activity.window.decorView.post { showGoalDialog(activity, pref, onChange) }
            }
            .setNeutralButton("分享") { _, _ ->
                ShareUtil.shareText(
                    activity,
                    "儿童锁屏 · 学习数据\n————————————\n$content",
                    "儿童锁屏学习数据",
                    "分享学习数据"
                )
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun buildContent(summary: StudyStatsSummary): String = buildString {
        if (summary.total <= 0) {
            append("近 7 天还没有答题记录。完成锁屏答题后，这里会按科目统计。\n\n")
        } else {
            append("今日：").append(summary.todayTotal).append(" 题")
            append("\n近 7 天：").append(summary.total).append(" 题 · 正确率 ")
                .append(summary.accuracyPercent).append("%\n\n")
        }

        if (summary.goalEnabled) {
            append("每日目标：").append(summary.goalQuestions).append(" 题（今日 ")
                .append(summary.todayTotal).append("/").append(summary.goalQuestions)
                .append("，").append(if (summary.todayGoalMet) "已达标" else "未达标").append("）\n")
            append("连续达标：").append(summary.streakDays).append(" 天 · 最长 ")
                .append(summary.bestStreakDays).append(" 天\n\n")
        } else {
            append("每日目标：未开启（点下方「每日目标」可设置）\n\n")
        }

        val subjects = summary.bySubject
            .filter { it.total > 0 }
            .sortedBy { orderOf(it.subject) }
        if (subjects.isNotEmpty()) {
            append("近 7 天分科：\n")
            for (stat in subjects) {
                append("· ").append(Subject.label(stat.subject)).append("  ")
                    .append(stat.total).append(" 题 · 正确率 ")
                    .append(stat.accuracyPercent).append("%\n")
            }
            append('\n')
        }

        if (summary.total > 0) {
            val weak = StudyStatsPolicy.weakestSubject(summary.bySubject)
            if (weak != null) {
                append("建议：").append(Subject.label(weak.subject))
                    .append("正确率 ").append(weak.accuracyPercent)
                    .append("%，").append(suggestion(weak.subject)).append("。")
            } else {
                append("近 7 天各科正确率较稳定，继续保持。")
            }
        }
    }

    private fun showGoalDialog(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val enabled = pref.isStudyGoalEnabled()
        val current = pref.getStudyGoalQuestions()
        val values = intArrayOf(0, 10, 20, 30)
        val labels = arrayOf(
            "关闭每日目标" + if (!enabled) "（当前）" else "",
            "10 题" + if (enabled && current == 10) "（当前）" else "",
            "20 题" + if (enabled && current == 20) "（当前）" else "",
            "30 题" + if (enabled && current == 30) "（当前）" else ""
        )
        AlertDialog.Builder(activity)
            .setTitle("每日答题目标")
            .setItems(labels) { _, which ->
                val questions = values[which]
                if (questions <= 0) {
                    pref.setStudyGoalEnabled(false)
                    AuditLog.record(activity, "设置", "关闭学习目标")
                } else {
                    pref.setStudyGoalQuestions(questions)
                    pref.setStudyGoalEnabled(true)
                    AuditLog.record(activity, "设置", "设置学习目标", "每日 $questions 题")
                }
                onChange()
                activity.window.decorView.post { show(activity, pref, onChange) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun orderOf(subject: String): Int {
        val index = Subject.ORDER.indexOf(subject)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun suggestion(subject: String): String = when (subject) {
        Subject.PINYIN -> "可以多读几遍易错字的拼音"
        Subject.MATH -> "可以多练口算和应用题"
        Subject.ENGLISH -> "可以多复习单词和词义"
        Subject.CITU -> "可以多读词语和成语"
        else -> "可以多做几道题巩固"
    }
}
