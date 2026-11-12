package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.kidslock.app.question.AdaptiveDifficulty
import com.kidslock.app.question.ReviewQuota

/** 题型与复习设置：判断题 / 填空题 / 错题加练与占比 / 自适应难度。 */
object QuestionTypeDialog {

    private class Item(val label: String, val action: () -> Unit)

    fun show(activity: Activity, pref: PrefManager, onChange: () -> Unit) {
        val adaptiveGrade = pref.getAdaptiveGrade(pref.grade)
        val boostOn = pref.isReviewBoostEnabled()
        val ratioLabel = ReviewQuota.label(pref.getReviewBoostRatio())
        // 填空题需要打字输入：电视/盒子没有触摸屏也没有软键盘，直接不提供这个入口
        val fillAvailable = DeviceProfile.hasTouchscreen(activity)

        val items = ArrayList<Item>()
        items.add(Item("判断题：${if (pref.isJudgmentEnabled()) "已开启（每 3 道穿插 1 道）" else "已关闭"}") {
            pref.setJudgmentEnabled(!pref.isJudgmentEnabled())
            onChange()
            toast(activity, if (pref.isJudgmentEnabled()) "已加入判断题" else "已关闭判断题")
        })
        if (fillAvailable) {
            items.add(Item("填空题：${if (pref.isFillBlankEnabled()) "已开启（数学题自己输入答案）" else "已关闭"}") {
                pref.setFillBlankEnabled(!pref.isFillBlankEnabled())
                onChange()
                toast(activity, if (pref.isFillBlankEnabled()) "已开启填空题（数学）" else "已关闭填空题")
            })
        }
        items.add(Item("错题加练：${if (boostOn) "已开启（到期错题优先，占 $ratioLabel）" else "已关闭"}") {
            pref.setReviewBoostEnabled(!pref.isReviewBoostEnabled())
            onChange()
            toast(activity, if (pref.isReviewBoostEnabled()) "已开启错题加练" else "已关闭错题加练")
        })
        items.add(Item("错题加练占比：$ratioLabel" + if (boostOn) "" else "（需先开启错题加练）") {
            if (!pref.isReviewBoostEnabled()) {
                toast(activity, "请先开启错题加练")
            } else {
                showRatioDialog(activity, pref, onChange)
            }
        })
        items.add(Item("自适应难度：${if (pref.isAdaptiveDifficultyEnabled()) "已开启" else "已关闭"}") {
            val enabled = !pref.isAdaptiveDifficultyEnabled()
            pref.setAdaptiveDifficultyEnabled(enabled)
            if (!enabled) pref.clearAdaptiveGrade()
            onChange()
            toast(
                activity,
                if (enabled) "已开启自适应难度：按最近 20 题正确率 ±1 档"
                else "已关闭自适应难度"
            )
        })
        items.add(Item(
            if (pref.isAdaptiveDifficultyEnabled()) AdaptiveDifficulty.describe(adaptiveGrade, pref.grade)
            else "说明：多选题暂未提供" + if (fillAvailable) "（填空题已支持数学）" else ""
        ) {
            toast(activity, "多选题后续版本再提供")
        })

        AlertDialog.Builder(activity)
            .setTitle("题型与复习")
            .setItems(items.map { it.label }.toTypedArray()) { _, which ->
                items[which].action.invoke()
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
