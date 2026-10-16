package com.kidslock.app.question

object Subject {
    const val PINYIN = "pinyin"
    const val MATH = "math"
    const val ENGLISH = "english"
    const val CITU = "ciyu"

    @JvmField
    val ORDER = arrayOf(PINYIN, MATH, ENGLISH, CITU)

    private val PHASE_ALLOWED = setOf(PINYIN, MATH, ENGLISH, CITU)
    private val KNOWN = setOf(PINYIN, MATH, ENGLISH, CITU)

    @JvmStatic
    fun isValid(subject: String): Boolean = subject in KNOWN

    @JvmStatic
    fun isPhaseAllowed(subject: String): Boolean = subject in PHASE_ALLOWED

    @JvmStatic
    fun label(subject: String): String = when (subject) {
        PINYIN -> "拼音"
        MATH -> "数学"
        ENGLISH -> "英语"
        CITU -> "语文"
        else -> subject
    }
}
