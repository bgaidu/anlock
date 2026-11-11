package com.kidslock.app.prefs

/**
 * 「最近 N 题」答对记录的纯计算，供自适应难度判断。
 * 记录用 1/0 串保存，只保留最近 window 题，避免无限增长。
 */
object RecentAnswers {
    const val WINDOW = 20

    fun append(history: String?, correct: Boolean, window: Int = WINDOW): String =
        ((history ?: "") + if (correct) "1" else "0").takeLast(window.coerceAtLeast(1))

    /** 最近 window 题的正确率（0-100）；样本不足返回 -1。 */
    fun accuracy(history: String?, window: Int = WINDOW): Int {
        val size = window.coerceAtLeast(1)
        val recent = (history ?: "").takeLast(size)
        if (recent.length < size) return -1
        val correct = recent.count { it == '1' }
        return Math.round(100f * correct / recent.length)
    }

    fun answeredCount(history: String?): Int = (history ?: "").length
}
