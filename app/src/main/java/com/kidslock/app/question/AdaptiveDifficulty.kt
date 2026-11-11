package com.kidslock.app.question

/**
 * 自适应难度的纯计算部分：按最近 N 题的正确率，在家长设定年级的基础上 ±1 档。
 *
 * 约束：
 * - 只在样本足够时调整，避免开局连对两题就跳级；
 * - 每次调整后由调用方清空最近答题记录，下一次要重新积累 N 题才会再动，形成滞回；
 * - 永远不偏离家长设定年级超过 1 档，家长仍可随时手动改回。
 */
object AdaptiveDifficulty {
    const val WINDOW = 20
    const val RAISE_ACCURACY = 85
    const val LOWER_ACCURACY = 50
    const val OFFSET_LIMIT = 1

    /** 有效年级的合法范围：家长设定年级 ± [OFFSET_LIMIT]，并夹在 0..6。 */
    fun range(baseGrade: Int): IntRange {
        val base = baseGrade.coerceIn(Grade.MIN, Grade.MAX)
        val low = (base - OFFSET_LIMIT).coerceAtLeast(Grade.MIN)
        val high = (base + OFFSET_LIMIT).coerceAtMost(Grade.MAX)
        return low..high
    }

    /**
     * @param current 当前生效年级（可能是上次调整后的结果）
     * @param base 家长设定的年级
     * @param recentAccuracy 最近 N 题正确率（0-100），样本不足时传 -1
     * @param sampleCount 最近答题样本数
     */
    fun next(
        current: Int,
        base: Int,
        recentAccuracy: Int,
        sampleCount: Int,
        enabled: Boolean
    ): Int {
        val allowed = range(base)
        if (!enabled) return base.coerceIn(Grade.MIN, Grade.MAX)
        val clamped = current.coerceIn(Grade.MIN, Grade.MAX)
        if (sampleCount < WINDOW || recentAccuracy < 0) return clamped.coerceIn(allowed)
        val desired = when {
            recentAccuracy >= RAISE_ACCURACY -> clamped + 1
            recentAccuracy <= LOWER_ACCURACY -> clamped - 1
            else -> clamped
        }
        return desired.coerceIn(allowed)
    }

    fun describe(current: Int, base: Int): String {
        val gradeText = Grade.fullLabel(current)
        return if (current == base) {
            "自适应：当前 $gradeText（与设定一致）"
        } else if (current > base) {
            "自适应：当前 $gradeText（已上调一档，设定 ${Grade.fullLabel(base)}）"
        } else {
            "自适应：当前 $gradeText（已下调一档，设定 ${Grade.fullLabel(base)}）"
        }
    }
}
