package com.kidslock.app.prefs

import android.content.SharedPreferences
import com.kidslock.app.prefs.PrefKeys.DEFAULT_QUESTION_GRADE
import com.kidslock.app.prefs.PrefKeys.DEFAULT_UNLOCK_COUNT
import com.kidslock.app.prefs.PrefKeys.ADAPTIVE_DIFFICULTY
import com.kidslock.app.prefs.PrefKeys.ADAPTIVE_GRADE
import com.kidslock.app.prefs.PrefKeys.QUESTION_FILL
import com.kidslock.app.prefs.PrefKeys.QUESTION_GRADE
import com.kidslock.app.prefs.PrefKeys.QUESTION_JUDGMENT
import com.kidslock.app.prefs.PrefKeys.QUESTION_SUBJECTS
import com.kidslock.app.prefs.PrefKeys.RECENT_ANSWERS
import com.kidslock.app.prefs.PrefKeys.REVIEW_BOOST
import com.kidslock.app.prefs.PrefKeys.SUBJECT_GRADE_PREFIX
import com.kidslock.app.prefs.PrefKeys.UNLOCK_COUNT
import com.kidslock.app.question.AdaptiveDifficulty

/** 出题设置：年级、分科年级、科目、题型开关、自适应难度与最近答题。 */
class QuestionStore(private val prefs: SharedPreferences) : QuestionSettings {

    override var grade: Int
        get() {
            val g = prefs.getInt(QUESTION_GRADE, DEFAULT_QUESTION_GRADE)
            return if (g in 0..6) g else DEFAULT_QUESTION_GRADE
        }
        set(value) {
            if (value < 0 || value > 6) return
            prefs.edit().putInt(QUESTION_GRADE, value).apply()
        }

    override var unlockCount: Int
        get() = prefs.getInt(UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT)
        set(value) {
            prefs.edit().putInt(UNLOCK_COUNT, value).apply()
        }

    override var subjects: Set<String>
        get() = SubjectSelection.parse(prefs.getString(QUESTION_SUBJECTS, null))
        set(value) {
            prefs.edit().putString(QUESTION_SUBJECTS, SubjectSelection.encode(value)).apply()
        }

    private fun subjectGradeRaw(subject: String): Int =
        prefs.getInt(SUBJECT_GRADE_PREFIX + subject, -1)

    override fun hasSubjectGrade(subject: String): Boolean = subjectGradeRaw(subject) in 0..6

    override fun gradeFor(subject: String): Int {
        val raw = subjectGradeRaw(subject)
        return if (raw in 0..6) raw else grade
    }

    override fun setSubjectGrade(subject: String, value: Int) {
        if (value < 0 || value > 6) return
        prefs.edit().putInt(SUBJECT_GRADE_PREFIX + subject, value).apply()
    }

    override fun clearSubjectGrade(subject: String) {
        prefs.edit().remove(SUBJECT_GRADE_PREFIX + subject).apply()
    }

    override fun isJudgmentEnabled(): Boolean =
        prefs.getBoolean(QUESTION_JUDGMENT, false)

    override fun setJudgmentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(QUESTION_JUDGMENT, enabled).apply()
    }

    override fun isReviewBoostEnabled(): Boolean = prefs.getBoolean(REVIEW_BOOST, true)

    override fun setReviewBoostEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(REVIEW_BOOST, enabled).apply()
    }

    override fun isFillBlankEnabled(): Boolean = prefs.getBoolean(QUESTION_FILL, false)

    override fun setFillBlankEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(QUESTION_FILL, enabled).apply()
    }

    override fun isAdaptiveDifficultyEnabled(): Boolean =
        prefs.getBoolean(ADAPTIVE_DIFFICULTY, false)

    override fun setAdaptiveDifficultyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ADAPTIVE_DIFFICULTY, enabled).apply()
    }

    override fun getAdaptiveGrade(baseGrade: Int): Int {
        val current = prefs.getInt(ADAPTIVE_GRADE, baseGrade)
        val allowed = AdaptiveDifficulty.range(baseGrade)
        val clamped = current.coerceIn(allowed)
        if (clamped != current) {
            prefs.edit().putInt(ADAPTIVE_GRADE, clamped).apply()
        }
        return clamped
    }

    override fun setAdaptiveGrade(grade: Int) {
        prefs.edit().putInt(ADAPTIVE_GRADE, grade).apply()
    }

    override fun clearAdaptiveGrade() {
        prefs.edit().remove(ADAPTIVE_GRADE).apply()
    }

    override fun consumeRecentAnswers() {
        prefs.edit().remove(RECENT_ANSWERS).apply()
    }

    override fun isSubjectEnabled(subject: String): Boolean = subjects.contains(subject)

    override fun setSubjectEnabled(subject: String, enabled: Boolean): Boolean {
        if (!SubjectSelection.VALID.contains(subject)) return false
        val current = HashSet(subjects)
        if (enabled) {
            current.add(subject)
        } else {
            if (current.size <= 1 && current.contains(subject)) return false
            current.remove(subject)
        }
        prefs.edit()
            .putString(QUESTION_SUBJECTS, SubjectSelection.encode(current))
            .apply()
        return true
    }

    override fun getRecentAccuracyPercent(window: Int): Int =
        RecentAnswers.accuracy(prefs.getString(RECENT_ANSWERS, ""), window)

    override fun getRecentAnsweredCount(): Int =
        RecentAnswers.answeredCount(prefs.getString(RECENT_ANSWERS, ""))
}
