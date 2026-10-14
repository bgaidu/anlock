package com.kidslock.app.question;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 干扰项采样（R23 可测核心，无 Android 依赖，可在 JVM 单元测试中直接调用）。
 *
 * <p>所有「同池取干扰项」逻辑集中于此，{@link BankProvider}/{@link EnglishProvider} 改调本类，
 * 不再内联采样（共享知识 #2）。库内仅依赖 {@link BankEntry}（其值对象本身亦无 Android 依赖），
 * 因此可脱离 context 在纯 JVM 下单测。</p>
 */
public final class DistractorSampler {

    private DistractorSampler() {
        // 工具类，禁止实例化
    }

    /**
     * 从同池取 3 个与正确项不同且互不相同的候选项，拼成 4 选项（含正确项），洗牌后返回。
     *
     * @param answerKey false → 以 v 为正确项取值（拼音/语文正向、英语正向）；
     *                 true  → 以 k 为正确项取值（英语反向）。
     * @return 4 个选项的洗牌列表（含正确项）；候选去重后 < 3 或选项不合法时返回 null（绝不占位）。
     */
    @Nullable
    public static List<String> sample(@NonNull List<BankEntry> pool,
                                      @NonNull BankEntry chosen, boolean answerKey,
                                      @NonNull Random random) {
        String chosenVal = answerKey ? chosen.k : chosen.v;

        Set<String> candidates = new HashSet<>();
        for (BankEntry e : pool) {
            String val = answerKey ? e.k : e.v;
            if (!val.equals(chosenVal)) candidates.add(val);
        }
        if (candidates.size() < 3) return null; // 凑不齐 3 个干扰项 → 失败

        List<String> cands = new ArrayList<>(candidates);
        Collections.shuffle(cands, random);

        List<String> options = new ArrayList<>(4);
        options.add(chosenVal);
        options.addAll(cands.subList(0, 3));
        Collections.shuffle(options, random); // 正确项随机落位

        // 4 选项文本互异且含正确项（设计 §9.5）
        if (!isOptionSetValid(options, chosenVal)) return null;
        return options;
    }

    /** 4 选项文本互不相同且含正确项（设计 §9.5 校验） */
    private static boolean isOptionSetValid(@NonNull List<String> options, @NonNull String answer) {
        if (options.size() != 4) return false;
        Set<String> seen = new HashSet<>();
        boolean hasAnswer = false;
        for (String o : options) {
            if (!seen.add(o)) return false; // 文本重复
            if (o.equals(answer)) hasAnswer = true;
        }
        return hasAnswer;
    }
}
