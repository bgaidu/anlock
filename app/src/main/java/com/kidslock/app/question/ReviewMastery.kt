package com.kidslock.app.question

/**
 * 错题掌握度判定（纯逻辑，便于单测）。
 *
 * 「已掌握」= 连续复习次数与间隔都达标，且下次复习日还没到；
 * 到期未复习的题无论历史多好，都会重新进入「待复习」。
 * 家长也可以在错题本里手动标记已掌握 / 恢复复习。
 */
object ReviewMastery {
    const val MASTERED_MIN_REPS = 4
    const val MASTERED_MIN_INTERVAL = 21
    const val MANUAL_INTERVAL_DAYS = 30

    enum class Status {
        DUE,
        SCHEDULED,
        MASTERED
    }

    data class State(
        val reps: Int,
        val intervalDays: Int,
        val dueDay: Long
    )

    fun status(reps: Int, intervalDays: Int, dueDay: Long, todayDay: Long): Status = when {
        dueDay <= todayDay -> Status.DUE
        reps >= MASTERED_MIN_REPS && intervalDays >= MASTERED_MIN_INTERVAL -> Status.MASTERED
        else -> Status.SCHEDULED
    }

    fun isMastered(reps: Int, intervalDays: Int, dueDay: Long, todayDay: Long): Boolean =
        status(reps, intervalDays, dueDay, todayDay) == Status.MASTERED

    /** 家长手动标记已掌握：给一个足够长的间隔，之后仍会按 SM-2 正常复习。 */
    fun masteredState(todayDay: Long): State = State(
        reps = MASTERED_MIN_REPS,
        intervalDays = MANUAL_INTERVAL_DAYS,
        dueDay = todayDay + MANUAL_INTERVAL_DAYS
    )

    /** 恢复复习：当作一道没复习过的错题，今天重新出现。 */
    fun restoreState(todayDay: Long): State = State(
        reps = 0,
        intervalDays = 0,
        dueDay = todayDay
    )

    fun label(status: Status): String = when (status) {
        Status.DUE -> "待复习"
        Status.SCHEDULED -> "已排期"
        Status.MASTERED -> "已掌握"
    }
}
