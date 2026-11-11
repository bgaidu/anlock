package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HashSet
import java.util.Random

class MathSpecTest {

    @Test
    fun newKinds_produceUsableProblems() {
        val kinds = arrayOf(
            MathSpec.MathKind.UNIT_CONVERT,
            MathSpec.MathKind.NUMBER_PATTERN,
            MathSpec.MathKind.WORD_PROBLEM
        )
        val rnd = Random(2026)
        for (kind in kinds) {
            for (i in 0 until 300) {
                val problem = MathSpec.generate(kind, rnd)
                assertNotNull("$kind 第 $i 次生成失败", problem)
                val p = problem!!
                assertTrue("$kind 答案应为数值: ${p.answerText}", p.answerText.isNotBlank())
                assertEquals("$kind 应有 3 个干扰项", 3, p.distractorTexts.size)
                assertEquals("$kind 选项应互异", 3, HashSet(p.distractorTexts).size)
                assertTrue("$kind 干扰项不应等于答案", p.distractorTexts.none { it == p.answerText })
                assertTrue(
                    "$kind 题干应包含问号",
                    p.stem.contains("?") || p.stem.contains("？")
                )
            }
        }
    }

    @Test
    fun allKinds_haveAtLeastOneGrade() {
        for (kind in MathSpec.MathKind.values()) {
            val used = MathSpec.BY_GRADE.any { it.contains(kind) }
            assertTrue("$kind 没有被任何年级使用", used)
        }
    }

    @Test
    fun fillBlankAnswer_isNumeric() {
        // 填空题只支持数字键盘，这三类新题型必须保持数值答案
        val rnd = Random(7)
        for (kind in arrayOf(
            MathSpec.MathKind.UNIT_CONVERT,
            MathSpec.MathKind.NUMBER_PATTERN,
            MathSpec.MathKind.WORD_PROBLEM
        )) {
            repeat(100) {
                val p = MathSpec.generate(kind, rnd)!!
                assertTrue(
                    "$kind 答案不是数值: ${p.answerText}",
                    Regex("-?\\d+").matches(p.answerText)
                )
            }
        }
    }
}
