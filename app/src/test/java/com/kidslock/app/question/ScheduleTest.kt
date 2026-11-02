package com.kidslock.app.question

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedHashSet
import java.util.Random

class ScheduleTest {

    private fun cap(total: Int, m: Int): Int =
        Math.ceil(total.toDouble() / m).toInt()

    private fun count(sched: List<String>, s: String): Int {
        var c = 0
        for (x in sched) if (s == x) c++
        return c
    }

    private val subs: LinkedHashSet<String> =
        LinkedHashSet(listOf("pinyin", "math", "english", "ciyu"))

    @Test
    fun everySubjectAtLeastOne() {
        val rnd = Random(1)
        for (total in 4..30) {
            val sched = QuestionEngine.buildSchedule(subs, total, rnd)
            for (s in subs) {
                assertTrue("$s 应至少出现 1 次", count(sched, s) >= 1)
            }
        }
    }

    @Test
    fun degenerateTotalFewerThanSubjects() {
        val rnd = Random(1)
        for (total in 1..3) {
            val sched = QuestionEngine.buildSchedule(subs, total, rnd)
            var nonEmpty = 0
            for (s in subs) if (count(sched, s) >= 1) nonEmpty++
            assertEquals("恰有前 $total 个科目出题", total, nonEmpty)
            assertEquals(total, sched.size)
        }
    }

    @Test
    fun capsNotExceededWhenTotalGreaterThan5() {
        val total = 10
        val c = cap(total, 4)
        val rnd = Random(2)
        for (trial in 0 until 50) {
            val sched = QuestionEngine.buildSchedule(subs, total, rnd)
            assertTrue("math <= cap($c)，实际=${count(sched, "math")}",
                count(sched, "math") <= c)
            assertTrue("english <= cap($c)，实际=${count(sched, "english")}",
                count(sched, "english") <= c)
            assertTrue("pinyin+ciyu <= cap($c)，实际=${count(sched, "pinyin") + count(sched, "ciyu")}",
                count(sched, "pinyin") + count(sched, "ciyu") <= c)
        }
    }

    @Test
    fun total5_noCapsButEachAtLeastOne() {
        val rnd = Random(3)
        for (trial in 0 until 20) {
            val sched = QuestionEngine.buildSchedule(subs, 5, rnd)
            assertEquals(5, sched.size)
            for (s in subs) assertTrue("$s 应至少出现 1 次", count(sched, s) >= 1)
        }
    }

    @Test
    fun singleSubject_allQuestions() {
        val one = LinkedHashSet(listOf("pinyin"))
        val rnd = Random(4)
        val sched = QuestionEngine.buildSchedule(one, 10, rnd)
        assertEquals(10, sched.size)
        for (x in sched) assertEquals("pinyin", x)
    }
}