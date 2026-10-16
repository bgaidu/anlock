package com.kidslock.app.question

object Grade {
    const val MIN = 0
    const val MAX = 6
    const val DEFAULT = 3

    val SHORT = arrayOf("学前", "一", "二", "三", "四", "五", "六")
    val FULL = arrayOf("学前班", "一年级", "二年级", "三年级", "四年级", "五年级", "六年级")

    @JvmStatic
    fun isValid(grade: Int): Boolean = grade in MIN..MAX

    @JvmStatic
    fun shortLabel(grade: Int): String = if (isValid(grade)) SHORT[grade] else grade.toString()

    @JvmStatic
    fun fullLabel(grade: Int): String = if (isValid(grade)) FULL[grade] else grade.toString()
}
