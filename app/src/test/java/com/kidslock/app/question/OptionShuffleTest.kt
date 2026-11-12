package com.kidslock.app.question

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionShuffleTest {

    private val options = listOf("78", "88", "76", "77")

    @Test
    fun correctAnswerTextStaysAtCorrectIndexAcrossSeeds() {
        for (seed in 1..50) {
            val result = OptionShuffle.shuffle(options, correctIndex = 3, random = Random(seed.toLong()))
            assertEquals(options[3], result.options[result.correctIndex])
            assertEquals(options.toSet(), result.options.toSet())
            assertTrue(result.correctIndex in result.options.indices)
        }
    }

    @Test
    fun firstOptionCanMove() {
        val moved = (1L..20L).any { seed ->
            val result = OptionShuffle.shuffle(options, correctIndex = 0, random = Random(seed))
            assertEquals(options[0], result.options[result.correctIndex])
            result.correctIndex != 0
        }
        assertTrue(moved)
    }

    @Test
    fun tinyOptionListIsReturnedUnchanged() {
        val single = listOf("only")
        val result = OptionShuffle.shuffle(single, correctIndex = 0, random = Random(1))
        assertEquals(single, result.options)
        assertEquals(0, result.correctIndex)
    }

    @Test
    fun invalidCorrectIndexIsReturnedUnchanged() {
        val result = OptionShuffle.shuffle(options, correctIndex = 9, random = Random(1))
        assertEquals(options, result.options)
        assertEquals(9, result.correctIndex)
    }
}
