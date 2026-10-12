package com.kidslock.app.question;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * 出题接口（设计 §3.2）。
 * 各科目 provider 实现本接口，由 {@link QuestionEngine} 统一调度。
 */
public interface QuestionProvider {
    /** 科目标识（Subject.PINYIN / MATH / ENGLISH） */
    @NonNull
    String subject();

    /**
     * 该年级是否有可用题库。
     * 例如英语低年级无词表时返回 false，引擎自动跳过该科目。
     */
    boolean isAvailable(int grade);

    /**
     * 出一道题。
     * 失败（干扰项凑不齐等）返回 null，绝不向 UI 抛异常（设计 §9.2）。
     */
    @Nullable
    Question next(int grade, @NonNull Random random);
}
