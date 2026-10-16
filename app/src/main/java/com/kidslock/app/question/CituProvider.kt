package com.kidslock.app.question

import android.content.Context

class CituProvider(context: Context) : BankProvider(
    context, Subject.CITU, "这个词语怎么读？", Question.StemStyle.NORMAL
)
