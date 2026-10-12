package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kidslock.app.question.MathSpec.MathKind;
import com.kidslock.app.question.MathSpec.MathProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 数学题型（算式求值）。
 * subject = MATH，hint = "结果是多少？"，题干用 NORMAL 档（56sp 算式）。
 * 数学所有年级（0..6）均可用（启用即出）；题目由 {@link MathSpec} 代码生成，永不依赖资产文件。
 */
public final class MathProvider implements QuestionProvider {
    @NonNull
    private final Context context;

    public MathProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public String subject() {
        return Subject.MATH;
    }

    /** 数学所有合法年级均可用 */
    @Override
    public boolean isAvailable(int grade) {
        return Grade.isValid(grade);
    }

    @Nullable
    @Override
    public Question next(int grade, @NonNull Random random) {
        if (!Grade.isValid(grade)) return null;
        MathKind kind = MathSpec.BY_GRADE[grade][random.nextInt(MathSpec.BY_GRADE[grade].length)];
        MathProblem problem = MathSpec.generate(kind, random);
        if (problem == null) return null;
        if (problem.distractorTexts.size() != 3) return null; // 凑不齐 3 个干扰项 → 失败

        List<String> options = new ArrayList<>(4);
        options.add(problem.answerText);
        options.addAll(problem.distractorTexts);

        // 校验 4 选项文本互异（设计 §9.5）
        Set<String> seen = new HashSet<>();
        for (String o : options) {
            if (!seen.add(o)) return null;
        }

        Collections.shuffle(options, random); // 正确项随机落位
        int correctIndex = options.indexOf(problem.answerText);
        if (correctIndex < 0) return null;

        return new Question(problem.stem, "结果是多少？", options, correctIndex,
                Subject.MATH, problem.id, Question.StemStyle.NORMAL);
    }
}
