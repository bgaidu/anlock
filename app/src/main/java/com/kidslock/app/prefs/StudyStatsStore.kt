package com.kidslock.app.prefs

import android.content.SharedPreferences
import com.kidslock.app.prefs.PrefKeys.DEFAULT_STUDY_GOAL_QUESTIONS
import com.kidslock.app.prefs.PrefKeys.REVIEW_STAT_PREFIX
import com.kidslock.app.prefs.PrefKeys.STUDY_BEST_STREAK
import com.kidslock.app.prefs.PrefKeys.STUDY_GOAL_ENABLED
import com.kidslock.app.prefs.PrefKeys.STUDY_GOAL_MET_PREFIX
import com.kidslock.app.prefs.PrefKeys.STUDY_GOAL_QUESTIONS
import com.kidslock.app.prefs.PrefKeys.STUDY_LAST_PRUNE
import com.kidslock.app.prefs.PrefKeys.STUDY_STAT_PREFIX
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 分科/分日学习统计与每日目标。
 *
 * 存储格式（SharedPreferences）：
 * - `study_day_<yyyy-MM-dd>_<subject>_total|correct`：当天该科答题数/正确数；
 * - `study_goal_met_<yyyy-MM-dd>`：当天是否达成目标（用于连续打卡）；
 * - `study_goal_enabled` / `study_goal_questions`：家长设置；
 * - `study_best_streak`：历史最长连续达标。
 *
 * 只保留最近 [KEEP_DAYS] 天，避免长期使用后 prefs 无限膨胀。
 */
class StudyStatsStore(private val prefs: SharedPreferences) : StudyStatsSettings {

    companion object {
        private const val KEEP_DAYS = 60
        private const val DAY_MS = 86_400_000L
        private const val MIN_GOAL = 5
        private const val MAX_GOAL = 100
    }

    private fun dateKey(calendar: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

    private fun dateKey(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMillis))

    private fun statKey(prefix: String, date: String, subject: String, field: String) =
        "$prefix${date}_${subject}_$field"

    private fun goalMetKey(date: String) = "$STUDY_GOAL_MET_PREFIX$date"

    override fun recordStudyAnswer(subject: String, correct: Boolean) {
        recordAnswer(STUDY_STAT_PREFIX, subject, correct)
        maybeMarkGoalMet()
    }

    override fun recordReviewAnswer(subject: String, correct: Boolean) {
        recordAnswer(REVIEW_STAT_PREFIX, subject, correct)
    }

    private fun recordAnswer(prefix: String, subject: String, correct: Boolean) {
        if (subject.isBlank()) return
        val today = dateKey()
        val totalKey = statKey(prefix, today, subject, "total")
        val correctKey = statKey(prefix, today, subject, "correct")
        val editor = prefs.edit()
            .putInt(totalKey, prefs.getInt(totalKey, 0) + 1)
        if (correct) {
            editor.putInt(correctKey, prefs.getInt(correctKey, 0) + 1)
        }
        if (prefs.getString(STUDY_LAST_PRUNE, "") != today) {
            editor.putString(STUDY_LAST_PRUNE, today)
            pruneOldKeys(editor)
        }
        editor.apply()
    }

    override fun getStudyStats(days: Int): StudyStatsSummary {
        val agg = aggregate(STUDY_STAT_PREFIX, days)
        return StudyStatsSummary(
            total = agg.total,
            correct = agg.correct,
            bySubject = agg.bySubject,
            todayTotal = agg.todayTotal,
            goalEnabled = isStudyGoalEnabled(),
            goalQuestions = getStudyGoalQuestions(),
            todayGoalMet = isTodayGoalMet(),
            streakDays = getStudyStreakDays(),
            bestStreakDays = getBestStudyStreak()
        )
    }

    override fun getReviewStats(days: Int): ReviewStatsSummary {
        val agg = aggregate(REVIEW_STAT_PREFIX, days)
        return ReviewStatsSummary(
            total = agg.total,
            correct = agg.correct,
            bySubject = agg.bySubject,
            todayTotal = agg.todayTotal
        )
    }

    private class Aggregate(
        val total: Int,
        val correct: Int,
        val bySubject: List<SubjectStudyStat>,
        val todayTotal: Int
    )

    /** 按「前缀 + 近 N 天」聚合，普通答题与错题复习共用一套口径。 */
    private fun aggregate(prefix: String, days: Int): Aggregate {
        val safeDays = days.coerceIn(1, KEEP_DAYS)
        val dates = LinkedHashSet<String>()
        val calendar = Calendar.getInstance()
        for (i in 0 until safeDays) {
            dates.add(dateKey(calendar))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val today = dateKey()

        val bySubject = LinkedHashMap<String, IntArray>()
        var total = 0
        var correct = 0
        var todayTotal = 0
        for ((key, value) in prefs.all) {
            if (!key.startsWith(prefix)) continue
            val rest = key.removePrefix(prefix)
            if (rest.length < 12 || rest[10] != '_') continue
            val date = rest.substring(0, 10)
            if (date !in dates) continue
            val tail = rest.substring(11)
            val field = tail.substringAfterLast('_', "")
            val subject = tail.substringBeforeLast('_', "")
            if (subject.isEmpty()) continue
            val count = (value as? Int) ?: continue
            val pair = bySubject.getOrPut(subject) { IntArray(2) }
            if (field == "total") {
                pair[0] += count
                total += count
                if (date == today) todayTotal += count
            } else if (field == "correct") {
                pair[1] += count
                correct += count
            }
        }

        val stats = bySubject.map { (subject, pair) ->
            SubjectStudyStat(subject, pair[0], pair[1])
        }
        return Aggregate(total, correct, stats, todayTotal)
    }

    override fun getTodayStudyTotal(): Int {
        val prefix = "$STUDY_STAT_PREFIX${dateKey()}_"
        var total = 0
        for ((key, value) in prefs.all) {
            if (key.startsWith(prefix) && key.endsWith("_total")) {
                total += (value as? Int) ?: 0
            }
        }
        return total
    }

    override fun isStudyGoalEnabled(): Boolean = prefs.getBoolean(STUDY_GOAL_ENABLED, false)

    override fun setStudyGoalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(STUDY_GOAL_ENABLED, enabled).apply()
        if (enabled) maybeMarkGoalMet()
    }

    override fun getStudyGoalQuestions(): Int =
        prefs.getInt(STUDY_GOAL_QUESTIONS, DEFAULT_STUDY_GOAL_QUESTIONS)
            .coerceIn(MIN_GOAL, MAX_GOAL)

    override fun setStudyGoalQuestions(questions: Int) {
        prefs.edit()
            .putInt(STUDY_GOAL_QUESTIONS, questions.coerceIn(MIN_GOAL, MAX_GOAL))
            .apply()
        if (isStudyGoalEnabled()) maybeMarkGoalMet()
    }

    override fun isTodayGoalMet(): Boolean =
        prefs.getBoolean(goalMetKey(dateKey()), false)

    override fun getStudyStreakDays(): Int {
        val metDays = HashSet<Int>()
        val calendar = Calendar.getInstance()
        for (daysAgo in 0 until KEEP_DAYS) {
            if (prefs.getBoolean(goalMetKey(dateKey(calendar)), false)) {
                metDays.add(daysAgo)
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return StudyStatsPolicy.currentStreak(metDays)
    }

    override fun getBestStudyStreak(): Int = prefs.getInt(STUDY_BEST_STREAK, 0)

    override fun resetStudyStats() {
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(STUDY_STAT_PREFIX) ||
                key.startsWith(REVIEW_STAT_PREFIX) ||
                key.startsWith(STUDY_GOAL_MET_PREFIX)
            ) {
                editor.remove(key)
            }
        }
        editor.remove(STUDY_BEST_STREAK)
        editor.remove(STUDY_LAST_PRUNE)
            .apply()
    }

    private fun maybeMarkGoalMet() {
        if (!isStudyGoalEnabled()) return
        val today = dateKey()
        val key = goalMetKey(today)
        if (prefs.getBoolean(key, false)) return
        if (getTodayStudyTotal() < getStudyGoalQuestions()) return
        prefs.edit().putBoolean(key, true).apply()
        val streak = getStudyStreakDays()
        if (streak > getBestStudyStreak()) {
            prefs.edit().putInt(STUDY_BEST_STREAK, streak).apply()
        }
    }

    /** 只清理学习统计相关键，别误删家长设置或其他模块的数据。 */
    private fun pruneOldKeys(editor: SharedPreferences.Editor) {
        val cutoff = dateKey(System.currentTimeMillis() - KEEP_DAYS * DAY_MS)
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith(STUDY_STAT_PREFIX)) {
                val date = key.removePrefix(STUDY_STAT_PREFIX).take(10)
                if (date.length == 10 && date < cutoff) editor.remove(key)
            } else if (key.startsWith(REVIEW_STAT_PREFIX)) {
                val date = key.removePrefix(REVIEW_STAT_PREFIX).take(10)
                if (date.length == 10 && date < cutoff) editor.remove(key)
            } else if (key.startsWith(STUDY_GOAL_MET_PREFIX)) {
                val date = key.removePrefix(STUDY_GOAL_MET_PREFIX).take(10)
                if (date.length == 10 && date < cutoff) editor.remove(key)
            }
        }
    }
}
