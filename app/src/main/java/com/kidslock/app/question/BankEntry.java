package com.kidslock.app.question;

import androidx.annotation.NonNull;

/**
 * 题库条目：k = 题干（汉字 / 单词）、v = 正确选项（拼音 / 词义）。
 * 拼音与英语共用同一结构（设计 §1.4 统一 Schema）。
 * 设计为不可变值对象，供 {@link BankRepository} 缓存与 provider 同池取样。
 */
public final class BankEntry {
    @NonNull
    public final String k;
    @NonNull
    public final String v;

    public BankEntry(@NonNull String k, @NonNull String v) {
        this.k = k;
        this.v = v;
    }
}
