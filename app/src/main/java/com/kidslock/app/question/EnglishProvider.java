package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Random;

/**
 * 英语题型（D3 落地）：正向（看英文选中文）+ 反向（看中文选英文）混合出题，按题随机选方向。
 * subject = ENGLISH，题干用 NORMAL 档（56sp 单词 / 释义）。
 * 题库来自 banks/english_&lt;grade&gt;.json（{@link BankRepository} 三级兜底；非拼音无 L3 内置，
 * 资产缺失即 unusable，由引擎换 provider 重抽，详见设计 §4.2）。
 *
 * <p>干扰项均取自同年级词表、与正确项取值不同、文本互异，绝不占位（与拼音共用 BankProvider 约束）。
 * 任一方向凑不齐 3 个合法干扰项 → {@link #next} 返回 null，由引擎换 provider / 重抽。</p>
 */
public final class EnglishProvider extends BankProvider {

    private static final String HINT_FORWARD = "这个单词是什么意思？";
    private static final String HINT_REVERSE = "这个意思对应的单词是？";

    public EnglishProvider(@NonNull Context context) {
        // 基类 hint 仅作占位（英语按方向动态切换），题干字号档位用 NORMAL
        super(context, Subject.ENGLISH, HINT_FORWARD, Question.StemStyle.NORMAL);
    }

    @Nullable
    @Override
    public Question next(int grade, @NonNull Random random) {
        BankRepository.LoadResult lr = BankRepository.get(context).load(subject, grade);
        if (!lr.usable()) return null;

        List<BankEntry> pool = lr.entries;
        if (pool.size() < 4) return null;

        boolean forward = random.nextBoolean();
        BankEntry chosen = pool.get(random.nextInt(pool.size()));

        String stem;
        String answer;
        String hint;
        List<String> options;
        if (forward) {
            // 正向：看英文选中文（题干=单词，选项=释义）
            stem = chosen.k;
            answer = chosen.v;
            hint = HINT_FORWARD;
            options = DistractorSampler.sample(pool, chosen, /*answerKey=*/false, random);
        } else {
            // 反向：看中文选英文（题干=释义，选项=单词）
            stem = chosen.v;
            answer = chosen.k;
            hint = HINT_REVERSE;
            options = DistractorSampler.sample(pool, chosen, /*answerKey=*/true, random);
        }
        if (options == null) return null; // 凑不齐 3 个干扰项 → 失败

        // options 已由 DistractorSampler 洗牌（含正确项），直接定位正确项
        int correctIndex = options.indexOf(answer);
        if (correctIndex < 0) return null;    // 防御：选项集校验失守

        // id 带方向前缀 → 正 / 反向在 RecentQuestionQueue 中视为不同题，互不抑制
        String dirTag = forward ? "fwd" : "rev";
        String id = subject + ":" + dirTag + ":" + chosen.k;
        return new Question(stem, hint, options, correctIndex, subject, id, stemStyle);
    }
}
