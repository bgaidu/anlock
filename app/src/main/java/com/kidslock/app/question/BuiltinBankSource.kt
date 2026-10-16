package com.kidslock.app.question

import android.util.Log

class BuiltinBankSource : BankSource {
    companion object {
        private const val TAG = "KidsLock-Question"
    }

    override fun load(subject: String, grade: Int): BankRepository.LoadResult? {
        if (subject != Subject.PINYIN) return null
        val builtin = BuiltinPinyinBank.entries()
        Log.e(TAG, "题库L3强制回退: subject=pinyin 原年级=$grade 使用内置${builtin.size}条")
        return BankRepository.LoadResult(builtin, 3, intArrayOf(grade))
    }

    override fun priority() = 3
}
