package com.kidslock.app.question

/**
 * SM-2 间隔重复（天级）。
 * 与存储解耦，便于单元测试；ReviewStore 负责持久化。
 */
object Sm2 {
    const val MIN_EASE = 1.3
    const val MAX_EASE = 2.8
    const val DEFAULT_EASE = 2.5

    data class State(
        val ease: Double = DEFAULT_EASE,
        val intervalDays: Int = 0,
        val dueDay: Long = 0,
        val reps: Int = 0,
        val lapses: Int = 0
    )

    /**
     * q=4 表示"答对但不算轻松"：间隔 1 天 → 6 天 → 上次间隔 × ease；
     * 答错则回到今天、ease 降低 0.2。
     */
    fun review(state: State, correct: Boolean, today: Long): State {
        return if (correct) {
            val reps = state.reps + 1
            val interval = when {
                reps <= 1 -> 1
                reps == 2 -> 6
                else -> Math.round(state.intervalDays * state.ease).toInt().coerceAtLeast(1)
            }
            val ease = (state.ease + 0.1 - (5 - 4) * (0.08 + (5 - 4) * 0.02))
                .coerceIn(MIN_EASE, MAX_EASE)
            State(ease, interval, today + interval, reps, state.lapses)
        } else {
            State(
                ease = (state.ease - 0.2).coerceIn(MIN_EASE, MAX_EASE),
                intervalDays = 0,
                dueDay = today,
                reps = 0,
                lapses = state.lapses + 1
            )
        }
    }
}
