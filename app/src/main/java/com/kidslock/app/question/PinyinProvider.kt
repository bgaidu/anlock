package com.kidslock.app.question

import android.content.Context

class PinyinProvider(context: Context) : BankProvider(
    context, Subject.PINYIN, "这个字读什么？", Question.StemStyle.HANZI
)
