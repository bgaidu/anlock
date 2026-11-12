package com.kidslock.app.question

import java.util.Random

/**
 * 错题复习时打乱选项顺序，避免孩子记住「答案在第几个」。
 * 纯函数 + 可注入 [Random]，便于单测；正确答案的文字始终跟着 correctIndex 走。
 */
object OptionShuffle {

    data class Result(val options: List<String>, val correctIndex: Int)

    fun shuffle(options: List<String>, correctIndex: Int, random: Random): Result {
        if (options.size < 2 || correctIndex !in options.indices) {
            return Result(options, correctIndex)
        }
        val order = MutableList(options.size) { it }
        for (i in order.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }
        val shuffled = order.map { options[it] }
        val newCorrect = order.indexOf(correctIndex)
        return Result(shuffled, if (newCorrect in shuffled.indices) newCorrect else correctIndex)
    }
}
