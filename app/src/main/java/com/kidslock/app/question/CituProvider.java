package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 语文题型（词语/成语，D4 落地）：单一正向——看词语/四字成语选拼音（与原始拼音科目一致，不做反向）。
 * subject = CITU，题干用 NORMAL 档（56sp，词语/成语同档）。
 * 题库来自 banks/ciyu_&lt;grade&gt;.json（{@link BankRepository} 三级兜底；非拼音无 L3 内置，
 * 资产缺失即 unusable，由引擎换 provider 重抽，详见设计 §4.2）。
 *
 * <p>出题逻辑完全复用 {@link BankProvider} 基类：抽条目 → 同池取 3 个其他词的拼音作干扰项
 * → 洗牌 → 建题，干扰项绝不占位（与英语共用约束）。方向固定为"正向"，故不重写 next。</p>
 */
public final class CituProvider extends BankProvider {

    private static final String HINT = "这个词语怎么读？";

    public CituProvider(@NonNull Context context) {
        // 题干=词语、正确项=拼音；hint 固定，stemStyle=NORMAL（56sp 词语/成语同档）
        super(context, Subject.CITU, HINT, Question.StemStyle.NORMAL);
    }
}
