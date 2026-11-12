package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.kidslock.app.question.AdaptiveDifficulty
import com.kidslock.app.question.ReviewQuota

/** 题型与复习设置：判断题 / 填空题 / 错题加练与占比 / 自适应难度。 */
object QuestionTypeDialog {

    fun show(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val adaptiveGrade = pref.getAdaptiveGrade(pref.grade)
        val boostOn = pref.isReviewBoostEnabled()
        val ratioLabel = ReviewQuota.label(pref.getReviewBoostRatio())
        val labels = arrayOf(
            "判断题：${if (pref.isJudgmentEnabled()) "已开启（每 3 道穿插 1 道）" else "已关闭"}",
            "填空题：${if (pref.isFillBlankEnabled()) "已开启（数学题自己输入答案）" else "已关闭"}",
            "错题加练：${if (boostOn) "已开启（到期错题优先，占 $ratioLabel）" else "已关闭"}",
            "错题加练占比：$ratioLabel" + if (boostOn) "" else "（需先开启错题加练）",
            "自适应难度：${if (pref.isAdaptiveDifficultyEnabled()) "已开启" else "已关闭"}",
            if (pref.isAdaptiveDifficultyEnabled()) AdaptiveDifficulty.describe(adaptiveGrade, pref.grade)
            else "说明：多选题暂未提供（填空题已支持数学）"
        )
        AlertDialog.Builder(activity)
            .setTitle("题型与复习")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        pref.setJudgmentEnabled(!pref.isJudgmentEnabled())
                        onChange()
                        toast(activity, if (pref.isJudgmentEnabled()) "已加入判断题" else "已关闭判断题")
                    }
                    1 -> {
                        pref.setFillBlankEnabled(!pref.isFillBlankEnabled())
                        onChange()
                        toast(activity, if (pref.isFillBlankEnabled()) "已开启填空题（数学）" else "已关闭填空题")
                    }
                    2 -> {
                        pref.setReviewBoostEnabled(!pref.isReviewBoostEnabled())
                        onChange()
                        toast(activity, if (pref.isReviewBoostEnabled()) "已开启错题加练" else "已关闭错题加练")
                    }
                    3 -> {
                        if (!pref.isReviewBoostEnabled()) {
                            toast(activity, "请先开启错题加练")
                        } else {
                            showRatioDialog(activity, pref, onChange)
                        }
                    }
                    4 -> {
                        val enabled = !pref.isAdaptiveDifficultyEnabled()
                        pref.setAdaptiveDifficultyEnabled(enabled)
                        if (!enabled) pref.clearAdaptiveGrade()
                        onChange()
                        toast(
                            activity,
                            if (enabled) "已开启自适应难度：按最近 20 题正确率 ±1 档"
                            else "已关闭自适应难度"
                        )
                    }
                    else -> toast(activity, "多选题后续版本再提供")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showRatioDialog(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val values = intArrayOf(ReviewQuota.THIRD, ReviewQuota.HALF, ReviewQuota.ALL)
        val labels = arrayOf(
            "1/3（默认，最多 5 道）",
            "1/2（最多 8 道）",
            "全部（到期错题不足时用普通题补足）"
        )
        val current = values.indexOf(pref.getReviewBoostRatio()).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("错题加练占比")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                pref.setReviewBoostRatio(values[which])
                onChange()
                toast(
                    activity,
                    if (values[which] == ReviewQuota.ALL) "本次解锁优先练全部到期错题"
                    else "错题加练占比已设为 ${ReviewQuota.label(values[which])}"
                )
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(activity: Activity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
