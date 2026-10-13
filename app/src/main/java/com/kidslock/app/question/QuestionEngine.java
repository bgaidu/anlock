package com.kidslock.app.question;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kidslock.app.PrefManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 出题引擎（设计 §3.2 / §4.1）。
 * 唯一出题入口：读 PrefManager 的年级与科目 → 构建已启用 provider 列表 → 每次
 * {@link #nextQuestion()} 随机挑 provider 出题、跨科目统一防重复（容量 10、重抽上限 20）。
 *
 * <p>契约（设计 §9.2 / R03 L3）：{@link #nextQuestion()} 永不返回 null、永不抛异常。
 * 最后一环是 {@link PinyinProvider} 经 {@link BuiltinPinyinBank}（编译进 dex）兜底，
 * 即使全部 assets JSON 被删除也能产出题目。</p>
 */
public final class QuestionEngine {
    private static final String TAG = "KidsLock-Question";
    private static final int MAX_ATTEMPTS = 20;

    @NonNull
    private final Context context;
    @NonNull
    private final List<QuestionProvider> providers;
    @NonNull
    private final RecentQuestionQueue recent;
    @NonNull
    private final Random random;
    private final int grade;

    private QuestionEngine(@NonNull Context context, @NonNull List<QuestionProvider> providers, int grade) {
        this.context = context.getApplicationContext();
        this.providers = providers;
        this.grade = grade;
        this.recent = new RecentQuestionQueue();
        this.random = new Random();
    }

    /**
     * 工厂：读 PrefManager 的年级与科目，按 Subject.ORDER 构建已启用的 provider 列表。
     * provider 列表为空（脏数据/资产缺失）→ 强制回退 [PinyinProvider] 并 Log.e（R03 L3）。
     */
    @NonNull
    public static QuestionEngine create(@NonNull Context ctx, @NonNull PrefManager pref) {
        int grade = pref.getGrade(); // 恒返回 0..6
        Set<String> subjects = pref.getSubjects(); // 有序、非空（已过滤+兜底）

        List<QuestionProvider> list = new ArrayList<>();
        for (String s : Subject.ORDER) {
            if (!subjects.contains(s)) continue;
            if (Subject.PINYIN.equals(s)) {
                list.add(new PinyinProvider(ctx));
            } else if (Subject.MATH.equals(s)) {
                list.add(new MathProvider(ctx));
            } else if (Subject.ENGLISH.equals(s)) {
                list.add(new EnglishProvider(ctx));
            } else if (Subject.CITU.equals(s)) {
                list.add(new CituProvider(ctx));
            }
        }

        if (list.isEmpty()) {
            Log.e(TAG, "QuestionEngine: 无可用科目，强制回退 PinyinProvider（R03 L3）");
            list.add(new PinyinProvider(ctx));
        }
        return new QuestionEngine(ctx, list, grade);
    }

    /** 实际生效科目（供日志/调试） */
    @NonNull
    public List<String> activeSubjects() {
        List<String> out = new ArrayList<>();
        for (QuestionProvider p : providers) out.add(p.subject());
        return out;
    }

    /**
     * 出下一道题。契约：永不返回 null、永不抛异常。
     * 正常路径最多重抽 20 次并跳过最近 10 题；若 20 次全撞最近队列，则放宽约束强制返回一道。
     */
    @NonNull
    public Question nextQuestion() {
        Question q = nextQuestion(false);
        if (q != null) return q;
        return nextQuestion(true); // 放宽 recency 约束
    }

    @Nullable
    private Question nextQuestion(boolean relax) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            QuestionProvider p = providers.get(random.nextInt(providers.size()));
            Question q = p.next(grade, random);
            if (q == null) continue;
            if (relax || !recent.contains(q.id)) {
                recent.offer(q.id);
                return q;
            }
        }
        return null;
    }
}
