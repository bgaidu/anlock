package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HashSet
import java.util.Random

class MathProviderTest {

    private fun digitCount(n: Long): Int {
        if (n <= 0) return 1
        var c = 0
        var x = n
        while (x > 0) {
            c++
            x /= 10
        }
        return c
    }

    private fun magDigit(text: String): Int {
        val dot = text.indexOf('.')
        var intPart = if (dot >= 0) text.substring(0, dot) else text
        intPart = intPart.replace("-", "")
        if (intPart.isEmpty()) return 1
        return digitCount(intPart.toLong())
    }

    @Test
    fun allGrades_produceValidOptions() {
        val p = MathProvider(null)
        val rnd = Random(42)
        for (grade in 0..6) {
            for (t in 0 until 200) {
                val q = p.next(grade, rnd) ?: error("grade=$grade 应能出题")
                assertEquals(4, q.options.size)
                val seen = HashSet<String>(q.options)
                assertEquals("选项应互异", 4, seen.size)
                val answer = q.options[q.correctIndex]
                for (i in q.options.indices) {
                    if (i != q.correctIndex) {
                        assertTrue("干扰项不应等于正确项", q.options[i] != answer)
                    }
                }
            }
        }
    }

    @Test
    fun integerQuestions_digitsWithinOne() {
        val p = MathProvider(null)
        val rnd = Random(7)
        var checked = 0
        outer@ for (grade in 0..6) {
            if (checked >= 50) break
            for (t in 0 until 300) {
                if (checked >= 50) break@outer
                val q = p.next(grade, rnd) ?: continue
                val answer = q.options[q.correctIndex]
                if (!Regex("-?\\d+").matches(answer)) continue
                val aMag = magDigit(answer)
                for (i in q.options.indices) {
                    if (i == q.correctIndex) continue
                    val d = q.options[i]
                    if (!Regex("-?\\d+").matches(d)) continue
                    val dMag = magDigit(d)
                    assertTrue("位数差应<=1: $answer vs $d", Math.abs(dMag - aMag) <= 1)
                    checked++
                }
            }
        }
        assertTrue("应至少检查到若干整数题", checked > 0)
    }
}