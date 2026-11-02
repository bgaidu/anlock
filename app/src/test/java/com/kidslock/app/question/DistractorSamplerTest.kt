package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DistractorSamplerTest {

    private fun pool(vararg kv: String): List<BankEntry> {
        val list = ArrayList<BankEntry>()
        var i = 0
        while (i + 1 < kv.size) {
            list.add(BankEntry(kv[i], kv[i + 1]))
            i += 2
        }
        return list
    }

    private fun assertValid4(options: List<String>, answer: String) {
        assertNotNull(options)
        assertEquals(4, options.size)
        val seen = HashSet<String>(options)
        assertEquals("选项文本应互异", 4, seen.size)
        assertTrue("选项应含正确项", options.contains(answer))
        for (o in options) assertNotNull(o)
    }

    @Test
    fun pinyinForward_returns4Distinct() {
        val p = pool("妈", "mā", "麻", "má", "马", "mǎ", "骂", "mà", "吗", "ma")
        val rnd = Random(1)
        for (t in 0 until 50) {
            val chosen = p[t % p.size]
            val opts = DistractorSampler.sample(p, chosen, false, rnd)!!
            assertValid4(opts, chosen.v)
        }
    }

    @Test
    fun englishForward_usesV() {
        val p = pool("apple", "苹果", "banana", "香蕉", "cat", "猫", "dog", "狗")
        val rnd = Random(2)
        for (t in 0 until 50) {
            val chosen = p[t % p.size]
            val opts = DistractorSampler.sample(p, chosen, false, rnd)!!
            assertValid4(opts, chosen.v)
        }
    }

    @Test
    fun englishReverse_usesK() {
        val p = pool("apple", "苹果", "banana", "香蕉", "cat", "猫", "dog", "狗")
        val rnd = Random(3)
        for (t in 0 until 50) {
            val chosen = p[t % p.size]
            val opts = DistractorSampler.sample(p, chosen, true, rnd)!!
            assertValid4(opts, chosen.k)
        }
    }

    @Test
    fun ciyuForward_usesV() {
        val p = pool("妈妈", "māma", "爸爸", "bàba", "哥哥", "gēge", "弟弟", "dìdi")
        val rnd = Random(4)
        for (t in 0 until 50) {
            val chosen = p[t % p.size]
            val opts = DistractorSampler.sample(p, chosen, false, rnd)!!
            assertValid4(opts, chosen.v)
        }
    }

    @Test
    fun insufficientCandidates_returnsNull() {
        val p = pool("妈", "mā", "麻", "mā", "马", "mā", "骂", "mā")
        val rnd = Random(5)
        val opts = DistractorSampler.sample(p, p[0], false, rnd)
        assertNull(opts)
    }

    @Test
    fun distractorsNotEqualToCorrect() {
        val p = pool("a", "A", "b", "B", "c", "C", "d", "D", "e", "E")
        val rnd = Random(6)
        for (t in 0 until 100) {
            val chosen = p[t % p.size]
            val opts = DistractorSampler.sample(p, chosen, false, rnd)!!
            assertValid4(opts, chosen.v)
            val correct = opts.indexOf(chosen.v)
            for (i in opts.indices) {
                if (i != correct) {
                    assertTrue("干扰项不应等于正确项", opts[i] != chosen.v)
                }
            }
        }
    }
}