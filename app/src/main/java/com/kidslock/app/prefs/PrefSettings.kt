package com.kidslock.app.prefs

import java.util.Calendar

/**
 * PrefManager 的存储契约。拆成六个内聚的小接口后：
 * - 每个 Store 只关心自己的一组键，读代码不必在 1000 行里找；
 * - PrefManager 通过 Kotlin 的 `by` 委托保持原有公开 API，调用方无需改动；
 * - 以后做多孩子档案时，把这些 Store 换成带 profile 前缀的实现即可。
 */
interface PinSettings {
    fun verifyPin(pin: String?): Boolean
    fun setParentPin(pin: String?): Boolean
    fun isPinModified(): Boolean
    fun shouldForcePinChange(): Boolean
    fun isPinUsable(): Boolean
    fun isPinLocked(): Boolean
    fun getPinLockRemainingMillis(): Long
    fun recordPinFailure()
    fun resetPinFailures()
}

interface QuestionSettings {
    var grade: Int
    var unlockCount: Int
    var subjects: Set<String>

    fun hasSubjectGrade(subject: String): Boolean
    fun gradeFor(subject: String): Int
    fun setSubjectGrade(subject: String, value: Int)
    fun clearSubjectGrade(subject: String)

    fun isJudgmentEnabled(): Boolean
    fun setJudgmentEnabled(enabled: Boolean)
    fun isReviewBoostEnabled(): Boolean
    fun setReviewBoostEnabled(enabled: Boolean)

    /** 错题加练占比：3 = 1/3（默认）、2 = 1/2、1 = 全部。 */
    fun getReviewBoostRatio(): Int
    fun setReviewBoostRatio(ratio: Int)
    fun isFillBlankEnabled(): Boolean
    fun setFillBlankEnabled(enabled: Boolean)
    fun isAdaptiveDifficultyEnabled(): Boolean
    fun setAdaptiveDifficultyEnabled(enabled: Boolean)
    fun getAdaptiveGrade(baseGrade: Int): Int
    fun setAdaptiveGrade(grade: Int)
    fun clearAdaptiveGrade()

    fun consumeRecentAnswers()
    fun isSubjectEnabled(subject: String): Boolean
    fun setSubjectEnabled(subject: String, enabled: Boolean): Boolean
    fun getRecentAccuracyPercent(window: Int = RecentAnswers.WINDOW): Int
    fun getRecentAnsweredCount(): Int
}

interface ScheduleSettings {
    fun isScheduleEnabled(): Boolean
    fun setScheduleEnabled(enabled: Boolean)
    fun isScheduleFreeMode(): Boolean
    fun setScheduleFreeMode(free: Boolean)

    fun getScheduleStart(day: Int): Int
    fun getScheduleEnd(day: Int): Int
    fun setScheduleWindow(day: Int, startMin: Int, endMin: Int)
    fun clearScheduleDay(day: Int)
    fun scheduleDayIndex(now: Calendar = Calendar.getInstance()): Int
    fun scheduleWindowLabel(day: Int): String
    fun isWithinSchedule(now: Calendar = Calendar.getInstance()): Boolean

    fun grantScheduleBypass(durationMs: Long = 30 * 60 * 1000L)
    fun isScheduleBypassActive(): Boolean
}

interface RewardSettings {
    fun getStarExchangeStars(): Int
    fun getStarExchangeMinutes(): Int
    fun setStarExchange(stars: Int, minutes: Int)

    fun getStars(): Int
    fun addStars(count: Int)

    /** 扣星（答错惩罚）。最低扣到 0，返回实际扣掉的数量。 */
    fun removeStars(count: Int): Int
    fun getBestCombo(): Int
    fun recordCombo(combo: Int)

    fun resetStats()
    fun getStatTotal(): Int
    fun getStatCorrect(): Int
    fun getAccuracyPercent(): Int
    fun recordAnswer(correct: Boolean)
}

interface StudyStatsSettings {
    /** 记录一道题的答题结果；subject 使用 Subject 的字符串值（pinyin/math/...）。 */
    fun recordStudyAnswer(subject: String, correct: Boolean)

    /** 记录一道错题复习结果（只统计错题池里再次出现的题）。 */
    fun recordReviewAnswer(subject: String, correct: Boolean)

    /** 近 [days] 天的分科错题复习统计。 */
    fun getReviewStats(days: Int = 7): ReviewStatsSummary

    /** 近 [days] 天的分科统计 + 今日目标/连续达标状态。 */
    fun getStudyStats(days: Int = 7): StudyStatsSummary
    fun getTodayStudyTotal(): Int

    fun isStudyGoalEnabled(): Boolean
    fun setStudyGoalEnabled(enabled: Boolean)
    fun getStudyGoalQuestions(): Int
    fun setStudyGoalQuestions(questions: Int)

    fun isTodayGoalMet(): Boolean
    fun getStudyStreakDays(): Int
    fun getBestStudyStreak(): Int

    /** 清空学习历史与连续达标，保留家长设置的每日目标。 */
    fun resetStudyStats()
}

interface AppOptionSettings {
    fun isAutoStart(): Boolean
    fun setAutoStart(enabled: Boolean)
    fun isEmergencyRebootUnlock(): Boolean
    fun setEmergencyRebootUnlock(enabled: Boolean)

    fun isShowCountdown(): Boolean
    fun setShowCountdown(enabled: Boolean)

    fun isWeeklyReportEnabled(): Boolean
    fun setWeeklyReportEnabled(enabled: Boolean)

    fun isEyeRestEnabled(): Boolean
    fun setEyeRestEnabled(enabled: Boolean)
    fun getEyeRestIntervalMinutes(): Int
    fun setEyeRestIntervalMinutes(minutes: Int)
    fun eyeRestLastMs(): Long
    fun markEyeRestShown()

    fun isSetupWizardDone(): Boolean
    fun setSetupWizardDone(done: Boolean)

    fun isLanEnabled(): Boolean
    fun setLanEnabled(enabled: Boolean)
    fun getLanToken(): String
    fun regenerateLanToken(): String

    fun getLastBootTime(): Long
    fun setLastBootTime(time: Long)
    fun getRebootCount(): Int
    fun setRebootCount(count: Int)

    fun allowSettingsTemporarily(durationMs: Long = 2 * 60 * 1000L)
    fun isSettingsAllowedNow(): Boolean
}

interface AppLimitSettings {
    fun isAppLimitEnabled(): Boolean
    fun setAppLimitEnabled(enabled: Boolean)

    fun getAppLimit(packageName: String): AppLimit
    fun setAppLimit(packageName: String, weekdayMinutes: Int, weekendMinutes: Int)
    fun clearAppLimit(packageName: String)
    fun configuredAppPackages(): Set<String>
    fun hasAppLimit(packageName: String): Boolean

    fun getAppLimitBonusMinutes(packageName: String): Int
    fun addAppLimitBonusMinutes(packageName: String, minutes: Int)
    fun getAppLimitUsageOffsetMs(packageName: String): Long
    fun setAppLimitUsageOffsetMs(packageName: String, offsetMs: Long)
}
