package com.kidslock.app.question

import kotlin.jvm.JvmField

data class Question(
    @JvmField val stem: String,
    @JvmField val hint: String,
    @JvmField val options: List<String>,
    @JvmField val correctIndex: Int,
    @JvmField val subject: String,
    @JvmField val id: String,
    @JvmField val stemStyle: StemStyle,
    /**
     * 填空题：由孩子在锁屏上输入答案（数学数字键盘），而不是点选项。
     * options 仍保留「正确答案 + 干扰项」，这样答错进入错题池后可以按选择题复习。
     */
    @JvmField val fillBlank: Boolean = false,
    /** 该题来自错题池（本次是复习题），用于单独统计复习正确率。 */
    @JvmField val isReview: Boolean = false
) {
    enum class StemStyle {
        HANZI,
        NORMAL
    }
}
