package com.kidslock.app.prefs

/** 单个科目的学习量（近 N 天或今日）。 */
data class SubjectStudyStat(
    val subject: String,
    val total: Int,
    val correct: Int
) {
    val accuracyPercent: Int
        get() = StudyStatsPolicy.accuracyPercent(total, correct)
}

/** 学习数据弹窗/周报共用的聚合结果。 */
data class StudyStatsSummary(
    val total: Int,
    val correct: Int,
    val bySubject: List<SubjectStudyStat>,
    val todayTotal: Int,
    val goalEnabled: Boolean,
    val goalQuestions: Int,
    val todayGoalMet: Boolean,
    val streakDays: Int,
    val bestStreakDays: Int
) {
    val accuracyPercent: Int
        get() = StudyStatsPolicy.accuracyPercent(total, correct)
}

/** 错题复习统计（只统计错题池里再次出现的题）。 */
data class ReviewStatsSummary(
    val total: Int,
    val correct: Int,
    val bySubject: List<SubjectStudyStat>,
    val todayTotal: Int
) {
    val accuracyPercent: Int
        get() = StudyStatsPolicy.accuracyPercent(total, correct)
}

/**
 * 学习统计的纯逻辑：不依赖 Android，便于 JVM 单测。
 *
 * 连续达标用「距今天数」表示：0=今天、1=昨天、2=前天……这样不用在纯逻辑里处理
 * 时区/夏令时/月末，Store 用 Calendar 逐天回看即可。
 */
object StudyStatsPolicy {

    const val MIN_WEAK_SAMPLES = 5
    const val WEAK_ACCURACY_THRESHOLD = 80

    fun accuracyPercent(total: Int, correct: Int): Int {
        if (total <= 0) return -1
        return Math.round(100f * correct.coerceIn(0, total) / total)
    }

    /**
     * 当前连续达标天数。今天还没达标不算断签（从昨天开始数），
     * 只有今天和昨天都没达标才是 0。
     */
    fun currentStreak(daysAgoMet: Set<Int>): Int {
        var day = if (0 in daysAgoMet) 0 else 1
        var streak = 0
        while (day in daysAgoMet) {
            streak++
            day++
        }
        return streak
    }

    /**
     * 薄弱科目：样本足够且正确率低于阈值时，取正确率最低的一科；
     * 否则返回 null（不给孩子贴标签）。
     */
    fun weakestSubject(
        stats: List<SubjectStudyStat>,
        minSamples: Int = MIN_WEAK_SAMPLES,
        threshold: Int = WEAK_ACCURACY_THRESHOLD
    ): SubjectStudyStat? {
        return stats.asSequence()
            .filter { it.total >= minSamples }
            .minByOrNull { it.accuracyPercent }
            ?.takeIf { it.accuracyPercent < threshold }
    }
}
