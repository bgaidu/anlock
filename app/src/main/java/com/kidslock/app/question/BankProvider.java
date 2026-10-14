package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 题库型 provider 抽象基类：拼音与英语共用。
 * 出题流程（设计 §4.1 / §9.5）：
 * 抽随机条目 → 同池取 3 个互不相同的干扰项（仅当去重后候选 ≥ 3 才可能）→ 洗牌 → 建题。
 * 任何一级凑不齐 3 个合法干扰项 → {@link #next} 返回 null，由引擎换 provider 重抽，绝不补占位。
 */
public abstract class BankProvider implements QuestionProvider {
    @NonNull
    protected final Context context;
    @NonNull
    protected final String subject;
    @NonNull
    protected final String hint;
    @NonNull
    protected final Question.StemStyle stemStyle;

    protected BankProvider(@NonNull Context context, @NonNull String subject,
                           @NonNull String hint, @NonNull Question.StemStyle stemStyle) {
        this.context = context.getApplicationContext();
        this.subject = subject;
        this.hint = hint;
        this.stemStyle = stemStyle;
    }

    @NonNull
    @Override
    public String subject() {
        return subject;
    }

    /** 是否有可用题库：委托 BankRepository 三级兜底判断 */
    @Override
    public boolean isAvailable(int grade) {
        return BankRepository.get(context).load(subject, grade).usable();
    }

    @Nullable
    @Override
    public Question next(int grade, @NonNull Random random) {
        BankRepository.LoadResult lr = BankRepository.get(context).load(subject, grade);
        if (!lr.usable()) return null;

        List<BankEntry> pool = lr.entries;
        if (pool.size() < 4) return null;

        BankEntry chosen = pool.get(random.nextInt(pool.size()));

        // 抽 3 个与正确项 v 不同且互不相同的干扰项（同池取样，杜绝占位文本），集中到 DistractorSampler
        List<String> options = DistractorSampler.sample(pool, chosen, /*answerKey=*/false, random);
        if (options == null) return null; // 凑不齐 3 个干扰项 → 失败
        return buildQuestion(chosen, options, random);
    }

    /** 由条目 + 已洗牌的 4 选项构建 Question */
    @NonNull
    protected Question buildQuestion(@NonNull BankEntry entry, @NonNull List<String> options,
                                     @NonNull Random random) {
        int correctIndex = options.indexOf(entry.v);
        String id = subject + ":" + entry.k;
        return new Question(entry.k, hint, options, correctIndex, subject, id, stemStyle);
    }
}
