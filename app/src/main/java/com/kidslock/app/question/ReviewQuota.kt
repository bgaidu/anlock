package com.kidslock.app.question

/**
 * 错题加练在本次解锁题量中的占比（纯逻辑，便于单测）。
 *
 * 比例用「分母」存储，兼容老版本的 `review_boost` 布尔开关：
 * - 3 = 最多占 1/3（默认，沿用旧行为，上限 5 道）
 * - 2 = 最多占 1/2（上限 8 道）
 * - 1 = 全部用到期错题（不足时由普通题补足）
 */
object ReviewQuota {
    const val THIRD = 3
    const val HALF = 2
    const val ALL = 1

    /** 非法值统一回落到 1/3，保证老数据与手改配置都安全。 */
    fun sanitize(ratio: Int): Int = when (ratio) {
        HALF, ALL -> ratio
        else -> THIRD
    }

    fun limit(total: Int, ratio: Int): Int {
        if (total <= 0) return 0
        return when (sanitize(ratio)) {
            ALL -> total
            HALF -> ((total + 1) / 2).coerceIn(1, 8)
            else -> (total / 3).coerceIn(1, 5)
        }
    }

    fun label(ratio: Int): String = when (sanitize(ratio)) {
        HALF -> "1/2"
        ALL -> "全部"
        else -> "1/3"
    }
}
