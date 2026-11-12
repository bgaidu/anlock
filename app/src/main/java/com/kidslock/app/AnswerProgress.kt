package com.kidslock.app

/**
 * 锁屏答题进度（纯逻辑，便于 JVM 单测）。
 *
 * 规则：答对累计进度，累计答对达到设定题数即解锁；答错不清零、也不提高目标，
 * 只是换一道新题继续（星星惩罚在 UI 层单独处理）。
 */
data class AnswerProgress(
    val correct: Int = 0,
    val required: Int
) {
    /** 还需答对多少题才能解锁。 */
    val remaining: Int
        get() = (required - correct).coerceAtLeast(0)

    val unlocked: Boolean
        get() = correct >= required

    fun onCorrect(): AnswerProgress = copy(correct = correct + 1)

    /** 答错：保留现有进度与目标，换题后继续。 */
    fun onWrong(): AnswerProgress = this
}
