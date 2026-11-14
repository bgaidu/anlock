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

    /**
     * 单位换算只允许「同量纲」的配对（米→厘米、千克→克、分钟→秒、升→毫升），
     * 且答案必须等于 数值 × 换算倍数。历史上真机抽查曾怀疑出现「米 → 毫升」，
     * 复查确认是终端按 GBK 解码 UTF-8 造成的乱码误报；这里用测试把这条约束固定下来，
     * 以后改错分支会立刻失败。
     */
    @Test
    fun unitConvert_usesOnlyConsistentUnitPairs() {
        val pairs = mapOf(
            "米" to ("厘米" to 100L),
            "千克" to ("克" to 1000L),
            "分钟" to ("秒" to 60L),
            "升" to ("毫升" to 1000L)
        )
        val pattern = Regex("^(\\d+) (\\S+) = \\? (\\S+)$")
        val rnd = Random(314)
        repeat(500) {
            val p = MathSpec.generate(MathSpec.MathKind.UNIT_CONVERT, rnd)!!
            val match = pattern.find(p.stem)
            assertNotNull("单位换算题干格式不对: ${p.stem}", match)
            val groups = match!!.groupValues
            val amount = groups[1].toLong()
            val from = groups[2]
            val to = groups[3]
            val pair = pairs[from]
            assertNotNull("未知的换算源单位: ${p.stem}", pair)
            assertEquals("单位配对错误: ${p.stem}", pair!!.first, to)
            assertEquals(
                "换算倍数错误: ${p.stem}",
                amount * pair.second,
                p.answerText.toLong()
            )
        }
    }
}
