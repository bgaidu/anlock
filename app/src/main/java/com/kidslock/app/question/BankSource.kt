package com.kidslock.app.question

interface BankSource {
    fun load(subject: String, grade: Int): BankRepository.LoadResult?
    fun priority(): Int
}
