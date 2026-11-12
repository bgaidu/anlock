package com.kidslock.app.prefs

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudyStatsStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: StudyStatsStore

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        store = StudyStatsStore(prefs)
    }

    @Test
    fun recordStudyAnswer_accumulatesTodayBySubject() {
        store.recordStudyAnswer("math", correct = true)
        store.recordStudyAnswer("math", correct = false)
        store.recordStudyAnswer("pinyin", correct = true)

        val today = dateKey(0)
        assertEquals(2, prefs.getInt("study_day_${today}_math_total", 0))
        assertEquals(1, prefs.getInt("study_day_${today}_math_correct", 0))
        assertEquals(1, prefs.getInt("study_day_${today}_pinyin_total", 0))
        assertEquals(1, prefs.getInt("study_day_${today}_pinyin_correct", 0))
        assertEquals(3, store.getTodayStudyTotal())
    }

    @Test
    fun wrongAnswer_doesNotIncreaseCorrectCount() {
        store.recordStudyAnswer("english", correct = false)

        val today = dateKey(0)
        assertEquals(1, prefs.getInt("study_day_${today}_english_total", 0))
        assertFalse(prefs.contains("study_day_${today}_english_correct"))
    }

    @Test
    fun blankSubject_isIgnored() {
        store.recordStudyAnswer("", correct = true)
        assertEquals(0, store.getTodayStudyTotal())
    }

    @Test
    fun recordReviewAnswer_isSeparateFromStudyStats() {
        store.recordStudyAnswer("math", correct = true)
        store.recordReviewAnswer("math", correct = false)
        store.recordReviewAnswer("math", correct = true)

        val today = dateKey(0)
        assertEquals(1, prefs.getInt("study_day_${today}_math_total", 0))
        assertEquals(2, prefs.getInt("review_day_${today}_math_total", 0))
        assertEquals(1, prefs.getInt("review_day_${today}_math_correct", 0))

        assertEquals(1, store.getStudyStats(7).total)
        val review = store.getReviewStats(7)
        assertEquals(2, review.total)
        assertEquals(1, review.correct)
        assertEquals(50, review.accuracyPercent)
        assertEquals(2, review.todayTotal)
    }

    @Test
    fun getReviewStats_filtersWindowAndAggregatesSubjects() {
        val today = dateKey(0)
        val yesterday = dateKey(1)
        val old = dateKey(40)
        prefs.edit()
            .putInt("review_day_${today}_math_total", 4)
            .putInt("review_day_${today}_math_correct", 3)
            .putInt("review_day_${yesterday}_english_total", 2)
            .putInt("review_day_${yesterday}_english_correct", 0)
            .putInt("review_day_${old}_math_total", 100)
            .putInt("review_day_${old}_math_correct", 100)
            .apply()

        val review = store.getReviewStats(7)
        assertEquals(6, review.total)
        assertEquals(3, review.correct)
        assertEquals(50, review.accuracyPercent)
        assertEquals(4, review.todayTotal)

        val math = review.bySubject.firstOrNull { it.subject == "math" }
        assertNotNull(math)
        assertEquals(4, math!!.total)
        assertEquals(75, math.accuracyPercent)

        val english = review.bySubject.firstOrNull { it.subject == "english" }
        assertNotNull(english)
        assertEquals(2, english!!.total)
        assertEquals(0, english.accuracyPercent)
    }

    @Test
    fun recordReviewAnswer_prunesOldReviewKeys() {
        val old = dateKey(90)
        prefs.edit().putInt("review_day_${old}_math_total", 3).apply()

        store.recordReviewAnswer("math", correct = true)

        assertFalse(prefs.contains("review_day_${old}_math_total"))
        assertTrue(prefs.contains("review_day_${dateKey(0)}_math_total"))
    }

    @Test
    fun blankReviewSubject_isIgnored() {
        store.recordReviewAnswer("", correct = true)
        assertEquals(0, store.getReviewStats(7).total)
    }

    @Test
    fun goalMet_turnsTrueWhenReachingTarget() {
        store.setStudyGoalQuestions(5)
        store.setStudyGoalEnabled(true)
        repeat(4) { store.recordStudyAnswer("math", correct = true) }
        assertFalse(store.isTodayGoalMet())

        store.recordStudyAnswer("math", correct = false)
        assertTrue(store.isTodayGoalMet())
        assertEquals(1, store.getStudyStreakDays())
        assertEquals(1, store.getBestStudyStreak())
    }

    @Test
    fun goalMet_notMarkedWhenGoalDisabled() {
        repeat(30) { store.recordStudyAnswer("math", correct = true) }
        assertFalse(store.isTodayGoalMet())
        assertEquals(0, store.getStudyStreakDays())
    }

    @Test
    fun streak_countsYesterdayChainWhenTodayNotDoneYet() {
        store.setStudyGoalQuestions(5)
        store.setStudyGoalEnabled(true)
        prefs.edit()
            .putBoolean("study_goal_met_${dateKey(1)}", true)
            .putBoolean("study_goal_met_${dateKey(2)}", true)
            .apply()

        assertEquals(2, store.getStudyStreakDays())
    }

    @Test
    fun streak_breaksOnMissingDay() {
        store.setStudyGoalQuestions(5)
        store.setStudyGoalEnabled(true)
        prefs.edit()
            .putBoolean("study_goal_met_${dateKey(0)}", true)
            .putBoolean("study_goal_met_${dateKey(2)}", true)
            .apply()

        assertEquals(1, store.getStudyStreakDays())
    }

    @Test
    fun getStudyStats_aggregatesSubjectsWithinWindowOnly() {
        val today = dateKey(0)
        val yesterday = dateKey(1)
        val old = dateKey(40)
        prefs.edit()
            .putInt("study_day_${today}_math_total", 10)
            .putInt("study_day_${today}_math_correct", 6)
            .putInt("study_day_${yesterday}_math_total", 4)
            .putInt("study_day_${yesterday}_math_correct", 4)
            .putInt("study_day_${today}_pinyin_total", 5)
            .putInt("study_day_${today}_pinyin_correct", 5)
            .putInt("study_day_${old}_math_total", 100)
            .putInt("study_day_${old}_math_correct", 1)
            .apply()

        val summary = store.getStudyStats(7)
        assertEquals(19, summary.total)
        assertEquals(15, summary.correct)
        assertEquals(15, summary.todayTotal)
        assertEquals(79, summary.accuracyPercent)

        val math = summary.bySubject.firstOrNull { it.subject == "math" }
        assertNotNull(math)
        assertEquals(14, math!!.total)
        assertEquals(10, math.correct)
        assertEquals(71, math.accuracyPercent)

        val pinyin = summary.bySubject.firstOrNull { it.subject == "pinyin" }
        assertNotNull(pinyin)
        assertEquals(5, pinyin!!.total)
        assertEquals(100, pinyin.accuracyPercent)
    }

    @Test
    fun getStudyStats_emptyHistory() {
        val summary = store.getStudyStats(7)
        assertEquals(0, summary.total)
        assertEquals(-1, summary.accuracyPercent)
        assertTrue(summary.bySubject.isEmpty())
        assertNull(summary.bySubject.firstOrNull())
    }

    @Test
    fun recordStudyAnswer_prunesDataOlderThanRetention() {
        val old = dateKey(90)
        val recent = dateKey(30)
        prefs.edit()
            .putInt("study_day_${old}_math_total", 1)
            .putBoolean("study_goal_met_$old", true)
            .putInt("study_day_${recent}_math_total", 2)
            .apply()

        store.recordStudyAnswer("math", correct = true)

        assertFalse(prefs.contains("study_day_${old}_math_total"))
        assertFalse(prefs.contains("study_goal_met_$old"))
        assertTrue(prefs.contains("study_day_${recent}_math_total"))
    }

    @Test
    fun resetStudyStats_clearsHistoryButKeepsGoalSettings() {
        store.setStudyGoalQuestions(30)
        store.setStudyGoalEnabled(true)
        repeat(30) { store.recordStudyAnswer("math", correct = true) }
        store.recordReviewAnswer("math", correct = true)
        assertTrue(store.isTodayGoalMet())

        store.resetStudyStats()

        assertEquals(0, store.getTodayStudyTotal())
        assertEquals(0, store.getReviewStats(7).total)
        assertFalse(store.isTodayGoalMet())
        assertEquals(0, store.getStudyStreakDays())
        assertEquals(0, store.getBestStudyStreak())
        assertTrue(store.isStudyGoalEnabled())
        assertEquals(30, store.getStudyGoalQuestions())
    }

    @Test
    fun goalQuestions_isClampedToSupportedRange() {
        store.setStudyGoalQuestions(1)
        assertEquals(5, store.getStudyGoalQuestions())
        store.setStudyGoalQuestions(999)
        assertEquals(100, store.getStudyGoalQuestions())
    }

    @Test
    fun malformedKeys_areIgnored() {
        prefs.edit()
            .putInt("study_day_short_math_total", 10)
            .putInt("study_day_${dateKey(0)}_math_bogus", 10)
            .putString("study_day_${dateKey(0)}_math_correct", "not-an-int")
            .apply()

        val summary = store.getStudyStats(7)
        assertEquals(0, summary.total)
    }

    private fun dateKey(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(calendar.timeInMillis))
    }
}
