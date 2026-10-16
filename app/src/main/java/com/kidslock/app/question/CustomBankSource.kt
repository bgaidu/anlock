package com.kidslock.app.question

import android.content.Context
import android.util.Log

class CustomBankSource(context: Context) : BankSource {
    companion object {
        private const val TAG = "KidsLock-Question"
    }

    private val appContext = context.applicationContext

    override fun load(subject: String, grade: Int): BankRepository.LoadResult? {
        if (!CustomBankStore.hasCustom(appContext, subject, grade)) return null
        val custom = CustomBankStore.loadCustom(appContext, subject, grade)
        if (custom != null && custom.usable()) {
            Log.i(TAG, "题库L0自定义命中: subject=$subject 年级=$grade")
            return custom
        }
        return null
    }

    override fun priority() = 0
}
