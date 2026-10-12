package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 拼音题型（看汉字选读音）。
 * subject = PINYIN，hint = "这个字读什么？"，题干用大字 HANZI 档。
 * 题库不足时经 BankRepository 三级兜底，最终落到 {@link BuiltinPinyinBank} 内置 144 条（L3）。
 */
public final class PinyinProvider extends BankProvider {
    public PinyinProvider(@NonNull Context context) {
        super(context, Subject.PINYIN, "这个字读什么？", Question.StemStyle.HANZI);
    }
}
