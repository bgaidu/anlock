package com.kidslock.app.question

import java.math.BigDecimal
import java.math.MathContext

/**
 * 填空答案比对：让孩子输入的内容与标准答案在做「数学等价」后比较。
 *
 * 覆盖的常见写法差异：
 * - 全角/半角（．／－）、多余空格；
 * - 小数尾零：`1.5` 与 `1.50`；
 * - 分数约分：`2/4` 与 `1/2`；
 * - 分数与小数互转：`1/2` 与 `0.5`（按 8 位有效数字比较）。
 */
object AnswerMatcher {
    private val FRACTION = Regex("^([+-]?\\d+)\\s*/\\s*(\\d+)$")
    private val NUMERIC = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$")
    private val COMPARISON = MathContext(8)

    fun matches(input: String?, expected: String?): Boolean {
        if (input == null || expected == null) return false
        val a = normalize(input)
        val b = normalize(expected)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true

        val fa = fractionOf(a)
        val fb = fractionOf(b)
        if (fa != null && fb != null) {
            return BigDecimal.valueOf(fa[0]).multiply(BigDecimal.valueOf(fb[1]))
                .compareTo(BigDecimal.valueOf(fb[0]).multiply(BigDecimal.valueOf(fa[1]))) == 0
        }

        val da = decimalOf(a) ?: fa?.let { ratio(it) }
        val db = decimalOf(b) ?: fb?.let { ratio(it) }
        if (da != null && db != null) {
            return da.round(COMPARISON).compareTo(db.round(COMPARISON)) == 0
        }
        return a.equals(b, ignoreCase = true)
    }

    fun normalize(raw: String): String = buildString {
        for (ch in raw) {
            when (ch) {
                '．', '。' -> append('.')
                '／' -> append('/')
                '－', '—', '−' -> append('-')
                '＋' -> append('+')
                '，', ',', ' ', '\t' -> Unit
                in '０'..'９' -> append('0' + (ch - '０'))
                else -> append(ch)
            }
        }
    }.trim()

    private fun fractionOf(text: String): LongArray? {
        val m = FRACTION.matchEntire(text) ?: return null
        val numerator = m.groupValues[1].toLongOrNull() ?: return null
        val denominator = m.groupValues[2].toLongOrNull() ?: return null
        if (denominator == 0L) return null
        return longArrayOf(numerator, denominator)
    }

    private fun decimalOf(text: String): BigDecimal? {
        if (!NUMERIC.matches(text)) return null
        return try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun ratio(frac: LongArray): BigDecimal? = try {
        BigDecimal.valueOf(frac[0]).divide(BigDecimal.valueOf(frac[1]), COMPARISON)
    } catch (e: ArithmeticException) {
        null
    }
}
