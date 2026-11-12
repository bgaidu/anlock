package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

/**
 * 每日总时长上限（平日 / 周末两套）。
 * 与「观看时长」（单次解锁多少分钟）区分：这里是孩子一天内所有观看时长的总和。
 */
object DailyLimitDialog {

    fun show(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val weekday = pref.getWeekdayDailyLimitMinutes()
        val weekend = pref.getWeekendDailyLimitMinutes()
        val items = arrayOf(
            "平日（周一至周五）：${limitLabel(weekday)}",
            "周末（周六、周日）：" +
                    if (weekend < 0) "跟随平日（${limitLabel(weekday)}）" else limitLabel(weekend),
            "额度按自然日 0 点重置；奖励与兑换会临时抬高当天上限"
        )
        AlertDialog.Builder(activity)
            .setTitle("每日总时长上限")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showPreset(activity, pref, weekend = false, onChange)
                    1 -> showPreset(activity, pref, weekend = true, onChange)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun shortLabel(minutes: Int): String =
        if (minutes <= 0) "不限" else "$minutes 分钟"

    private fun limitLabel(minutes: Int): String =
        if (minutes <= 0) "未开启（不限制）" else "$minutes 分钟"

    private fun showPreset(
        activity: Activity,
        pref: PrefManager,
        weekend: Boolean,
        onChange: () -> Unit
    ) {
        val values = if (weekend) intArrayOf(-1, 0, 60, 90, 120, 180) else intArrayOf(0, 60, 90, 120, 180)
        val labels = if (weekend)
            arrayOf("跟随平日", "关闭（不限总时长）", "60 分钟", "90 分钟", "120 分钟", "180 分钟", "自定义…")
        else
            arrayOf("关闭（不限总时长）", "60 分钟", "90 分钟", "120 分钟", "180 分钟", "自定义…")
        AlertDialog.Builder(activity)
            .setTitle(if (weekend) "周末每日上限" else "平日每日上限")
            .setItems(labels) { _, which ->
                if (which == labels.size - 1) {
                    showCustom(activity, pref, weekend, onChange)
                } else {
                    apply(activity, pref, weekend, values[which], onChange)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun apply(
        activity: Activity,
        pref: PrefManager,
        weekend: Boolean,
        minutes: Int,
        onChange: () -> Unit
    ) {
        if (weekend) pref.setWeekendDailyLimitMinutes(minutes) else pref.setDailyLimitMinutes(minutes)
        onChange()
        val where = if (weekend) "周末" else "平日"
        val valueLabel = when {
            minutes < 0 -> "跟随平日"
            minutes == 0 -> "不限"
            else -> "$minutes 分钟"
        }
        AuditLog.record(activity, "设置", "修改每日额度", "$where $valueLabel")
        Toast.makeText(
            activity,
            when {
                minutes < 0 -> "${where}额度已改为跟随平日"
                minutes == 0 -> "已关闭${where}上限"
                else -> "${where}每日上限设为 $minutes 分钟"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showCustom(
        activity: Activity,
        pref: PrefManager,
        weekend: Boolean,
        onChange: () -> Unit
    ) {
        val current = if (weekend) pref.getWeekendDailyLimitMinutes() else pref.getWeekdayDailyLimitMinutes()
        val input = EditText(activity).apply {
            setText(if (current < 0) "" else current.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1-720 分钟，填 0 表示不限制"
            setPadding(32, 24, 32, 24)
            setSelection(text.length)
        }
        AlertDialog.Builder(activity)
            .setTitle(if (weekend) "自定义周末每日上限" else "自定义平日每日上限")
            .setMessage("这是孩子一天内所有观看时长的总和，与「观看时长」（单次解锁多少分钟）是两回事。")
            .setView(input)
            .setPositiveButton("保存", DialogInterface.OnClickListener { _, _ ->
                val minutes = input.text.toString().toIntOrNull()
                if (minutes == null || minutes !in 0..720) {
                    Toast.makeText(activity, "请输入 0-720 之间的分钟数", Toast.LENGTH_SHORT).show()
                } else {
                    apply(activity, pref, weekend, minutes, onChange)
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
