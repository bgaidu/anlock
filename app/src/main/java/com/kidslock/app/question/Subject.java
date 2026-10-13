package com.kidslock.app.question;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 科目常量与中文名映射。
 * 本期 {@link #PINYIN} / {@link #MATH} / {@link #ENGLISH} / {@link #CITU}(语文) 均可启用
 * （英语 english_0..6.json + EnglishProvider、语文 ciyu_0..6.json + CituProvider 已就位）。
 *
 * 约定（设计 §9.1）：科目字面量只允许引用本类的 PINYIN/MATH/ENGLISH/CITU 常量，禁止散落字符串。
 */
public final class Subject {
    public static final String PINYIN = "pinyin";
    public static final String MATH = "math";
    public static final String ENGLISH = "english";
    public static final String CITU = "ciyu";   // 语文（词语/成语，看词选拼音）

    /** 固定展示 / 校验顺序：拼音 → 数学 → 英语 → 语文（getSubjects 按此排序） */
    public static final String[] ORDER = {PINYIN, MATH, ENGLISH, CITU};

    /** 本期可启用科目集合（拼音 / 数学 / 英语 / 语文） */
    private static final Set<String> PHASE_ALLOWED =
            new HashSet<>(Arrays.asList(PINYIN, MATH, ENGLISH, CITU));
    /** 所有已知科目常量（用于合法性校验，包含 english / ciyu） */
    private static final Set<String> KNOWN =
            new HashSet<>(Arrays.asList(PINYIN, MATH, ENGLISH, CITU));

    private Subject() {
        // 工具类，禁止实例化
    }

    /** 是否为已知科目常量（pinyin/math/english） */
    public static boolean isValid(@NonNull String subject) {
        return KNOWN.contains(subject);
    }

    /** 是否为本期可启用科目（拼音/数学/英语） */
    public static boolean isPhaseAllowed(@NonNull String subject) {
        return PHASE_ALLOWED.contains(subject);
    }

    /** 中文名：拼音 / 数学 / 英语 / 语文（未知值回退为原串） */
    @NonNull
    public static String label(@NonNull String subject) {
        if (PINYIN.equals(subject)) return "拼音";
        if (MATH.equals(subject)) return "数学";
        if (ENGLISH.equals(subject)) return "英语";
        if (CITU.equals(subject)) return "语文";
        return subject;
    }
}
