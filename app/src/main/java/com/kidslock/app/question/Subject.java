package com.kidslock.app.question;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 科目常量与中文名映射。
 * 本期 {@link #PINYIN} + {@link #MATH} + {@link #ENGLISH} 均可启用（英语 D3 已落地：
 * english_0..6.json + EnglishProvider + 合法集合已就位）。
 *
 * 约定（设计 §9.1）：科目字面量只允许引用本类的 PINYIN/MATH/ENGLISH 常量，禁止散落字符串。
 */
public final class Subject {
    public static final String PINYIN = "pinyin";
    public static final String MATH = "math";
    public static final String ENGLISH = "english";

    /** 固定展示 / 校验顺序：拼音 → 数学 → 英语（getSubjects 按此排序） */
    public static final String[] ORDER = {PINYIN, MATH, ENGLISH};

    /** 本期可启用科目集合（拼音 / 数学 / 英语，D3 英语已落地） */
    private static final Set<String> PHASE_ALLOWED =
            new HashSet<>(Arrays.asList(PINYIN, MATH, ENGLISH));
    /** 所有已知科目常量（用于合法性校验，包含 english） */
    private static final Set<String> KNOWN =
            new HashSet<>(Arrays.asList(PINYIN, MATH, ENGLISH));

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

    /** 中文名：拼音 / 数学 / 英语（未知值回退为原串） */
    @NonNull
    public static String label(@NonNull String subject) {
        if (PINYIN.equals(subject)) return "拼音";
        if (MATH.equals(subject)) return "数学";
        if (ENGLISH.equals(subject)) return "英语";
        return subject;
    }
}
