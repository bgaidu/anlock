package com.kidslock.app.question;

import androidx.annotation.NonNull;

/**
 * 年级编码与标签（0=学前 … 6=六年级）。
 * 年级边界常量与标签集中在此处，禁止在其它文件散落硬编码年级字面值。
 * 与设计 §9.1 一致：边界常量用 {@link #MIN} / {@link #MAX} / {@link #DEFAULT}。
 */
public final class Grade {
    /** 最小年级编码：学前 */
    public static final int MIN = 0;
    /** 最大年级编码：六年级 */
    public static final int MAX = 6;
    /** 默认年级（D6 修订：原五年级，现三年级；注意实际生效的默认年级由 PrefManager.DEFAULT_QUESTION_GRADE 驱动，本常量仅作边界/展示参考，当前未被直接引用） */
    public static final int DEFAULT = 3;

    /** 短标：学前/一/二/三/四/五/六（设置页按钮用极简短标） */
    public static final String[] SHORT = {"学前", "一", "二", "三", "四", "五", "六"};
    /** 全称：学前班/一年级 … 六年级（Toast 与权威显示用） */
    public static final String[] FULL = {"学前班", "一年级", "二年级", "三年级", "四年级", "五年级", "六年级"};

    private Grade() {
        // 工具类，禁止实例化
    }

    /** 年级是否在合法范围 0..6 */
    public static boolean isValid(int grade) {
        return grade >= MIN && grade <= MAX;
    }

    /** 短标签，越界时回退为数字字符串（不抛异常） */
    @NonNull
    public static String shortLabel(int grade) {
        return isValid(grade) ? SHORT[grade] : String.valueOf(grade);
    }

    /** 全称标签，越界时回退为数字字符串（不抛异常） */
    @NonNull
    public static String fullLabel(int grade) {
        return isValid(grade) ? FULL[grade] : String.valueOf(grade);
    }
}
