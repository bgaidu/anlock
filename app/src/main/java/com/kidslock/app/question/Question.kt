package com.kidslock.app.question

import kotlin.jvm.JvmField

data class Question(
    @JvmField val stem: String,
    @JvmField val hint: String,
    @JvmField val options: List<String>,
    @JvmField val correctIndex: Int,
    @JvmField val subject: String,
    @JvmField val id: String,
    @JvmField val stemStyle: StemStyle
) {
    enum class StemStyle {
        HANZI,
        NORMAL
    }
}
