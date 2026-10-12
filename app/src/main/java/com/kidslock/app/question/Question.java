package com.kidslock.app.question;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 通用题目模型（所有科目共用）。
 * <ul>
 *   <li>{@link #stem} 题干：誉 / 7 + 8 = ? / apple</li>
 *   <li>{@link #hint} 提示语：这个字读什么？ / 结果是多少？</li>
 *   <li>{@link #options} 恒为 4 个，互不重复（文本与数值都不同）</li>
 *   <li>{@link #correctIndex} 正确项随机位置 0..3</li>
 *   <li>{@link #subject} 科目标识（Subject.PINYIN / MATH / ENGLISH）</li>
 *   <li>{@link #id} 防重复唯一标识</li>
 *   <li>{@link #stemStyle} 题干字号档位：HANZI=130sp 大字，NORMAL=56sp 算式/单词</li>
 * </ul>
 */
public final class Question {
    /** 题干字号档位 */
    public enum StemStyle {
        /** 汉字大字（130sp） */
        HANZI,
        /** 算式 / 单词（56sp） */
        NORMAL
    }

    @NonNull
    public final String stem;
    @NonNull
    public final String hint;
    @NonNull
    public final List<String> options;
    public final int correctIndex;
    @NonNull
    public final String subject;
    @NonNull
    public final String id;
    @NonNull
    public final StemStyle stemStyle;

    public Question(@NonNull String stem, @NonNull String hint, @NonNull List<String> options,
                    int correctIndex, @NonNull String subject, @NonNull String id,
                    @NonNull StemStyle stemStyle) {
        this.stem = stem;
        this.hint = hint;
        this.options = options;
        this.correctIndex = correctIndex;
        this.subject = subject;
        this.id = id;
        this.stemStyle = stemStyle;
    }
}
