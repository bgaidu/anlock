package com.kidslock.app.question

import java.util.Random

interface QuestionProvider {
    fun subject(): String
    fun isAvailable(grade: Int): Boolean
    fun next(grade: Int, random: Random): Question?
}
