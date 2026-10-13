package com.kidslock.app.question;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kidslock.app.PrefManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 出题引擎（设计 §3.2 / §4.1）。
 * 唯一出题入口：读 PrefManager 的年级、科目与解锁题数 N → 构建已启用 provider 列表 →
 * 按「每科目出题数量规则」预算出长度为 N 的科目顺序表（schedule）→ 每次 {@link #nextQuestion()}
 * 依次从顺序表取科目出题、跨科目统一防重复（容量 10、重抽上限 20）。
 *
 * <p>每科目出题数量规则（出题总数量 N = 解锁题数，M = 启用科目数）：
 * ① 任一启用科目数量 ≥ 1（保证每种都练到）；
 * ② 当 N &gt; 5 时额外硬约束（上限 = ⌈N/M⌉，向上取整）：数学 ≤ ⌈N/M⌉、英语 ≤ ⌈N/M⌉、
 *    拼音 + 语文合计 ≤ ⌈N/M⌉；此三档上限**永不突破**；
 * ③ N == 5 时仅满足 ①（不设单科上限）。
 * 当启用科目数较少导致上限之和 &lt; N（如 4 科且 N=10 → 上限合计仅 9）时，严格遵守单科上限、
 * 放弃"总数恰为 N"——此时顺序表长度 &lt; N，但引擎循环重洗顺序表，解锁所需的 N 次作答仍可达成
 * （题目会重复，绝不无题可出）。此取舍令用户显式要求的单科上限硬约束永远不被打破。</p>
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
    private final Map<String, QuestionProvider> providerBySubject;
    @NonNull
    private final List<String> schedule;   // 长度为 N 的科目顺序表（已洗牌）
    private int schedulePos = 0;
    @NonNull
    private final RecentQuestionQueue recent;
    @NonNull
    private final Random random;
    private final int grade;

    private QuestionEngine(@NonNull Context context, @NonNull List<QuestionProvider> providers,
                           @NonNull Map<String, QuestionProvider> providerBySubject,
                           @NonNull List<String> schedule, int grade) {
        this.context = context.getApplicationContext();
        this.providers = providers;
        this.providerBySubject = providerBySubject;
        this.schedule = schedule;
        this.grade = grade;
        this.recent = new RecentQuestionQueue();
        this.random = new Random();
    }

    /**
     * 工厂：读 PrefManager 的年级、科目与解锁题数，构建已启用 provider 列表与科目顺序表。
     * provider 列表为空（脏数据/资产缺失）→ 强制回退 [PinyinProvider] 并 Log.e（R03 L3）。
     */
    @NonNull
    public static QuestionEngine create(@NonNull Context ctx, @NonNull PrefManager pref) {
        int grade = pref.getGrade(); // 恒返回 0..6
        Set<String> subjects = pref.getSubjects(); // 有序、非空（已过滤+兜底）
        int total = pref.getUnlockCount();          // 出题总数量 N（= 解锁题数）

        List<QuestionProvider> list = new ArrayList<>();
        Map<String, QuestionProvider> bySubject = new HashMap<>();
        for (String s : Subject.ORDER) {
            if (!subjects.contains(s)) continue;
            QuestionProvider p;
            if (Subject.PINYIN.equals(s)) {
                p = new PinyinProvider(ctx);
            } else if (Subject.MATH.equals(s)) {
                p = new MathProvider(ctx);
            } else if (Subject.ENGLISH.equals(s)) {
                p = new EnglishProvider(ctx);
            } else if (Subject.CITU.equals(s)) {
                p = new CituProvider(ctx);
            } else {
                continue;
            }
            list.add(p);
            bySubject.put(s, p);
        }

        if (list.isEmpty()) {
            Log.e(TAG, "QuestionEngine: 无可用科目，强制回退 PinyinProvider（R03 L3）");
            PinyinProvider p = new PinyinProvider(ctx);
            list.add(p);
            bySubject.put(Subject.PINYIN, p);
            subjects = Collections.singleton(Subject.PINYIN);
        }

        List<String> schedule = buildSchedule(subjects, total, new Random());
        return new QuestionEngine(ctx, list, bySubject, schedule, grade);
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
            QuestionProvider p = providerForScheduledSubject();
            if (p == null) p = providers.get(random.nextInt(providers.size()));
            Question q = p.next(grade, random);
            if (q == null) continue;
            if (relax || !recent.contains(q.id)) {
                recent.offer(q.id);
                return q;
            }
        }
        return null;
    }

    /** 取科目顺序表的下一个科目对应的 provider，并推进游标（越界则重洗继续） */
    @Nullable
    private QuestionProvider providerForScheduledSubject() {
        if (schedule.isEmpty()) return null;
        String subj = schedule.get(schedulePos);
        schedulePos++;
        if (schedulePos >= schedule.size()) {
            schedulePos = 0;
            Collections.shuffle(schedule, random);
        }
        return providerBySubject.get(subj);
    }

    /**
     * 预算长度为 total 的科目顺序表，满足出题数量规则：
     * 每科目 ≥ 1；当 total &gt; 5 时单科上限 = ⌈total / M⌉（M = 启用科目数），
     * 数学 ≤ 上限、英语 ≤ 上限、拼音+语文合计 ≤ 上限；total == 5 时不设单科上限。
     * 返回已洗牌的科目标签列表。
     */
    @NonNull
    private static List<String> buildSchedule(@NonNull Set<String> subjects, int total,
                                              @NonNull Random rnd) {
        if (total < 1) total = 1;
        List<String> list = new ArrayList<>(subjects); // 沿用 Subject.ORDER 顺序，结果确定
        Map<String, Integer> counts = new HashMap<>();
        for (String s : list) counts.put(s, 1); // 每科目至少 1 题

        // 题目数少于启用科目数（仅历史遗留小题数）：优先保留前 total 个科目各 1 题
        int remaining = total - list.size();
        if (remaining < 0) {
            for (int k = total; k < list.size(); k++) counts.put(list.get(k), 0);
            remaining = 0;
        }

        int m = list.size();                                  // 启用科目数 M
        boolean applyCaps = (total > 5);
        int cap = applyCaps ? (int) Math.ceil((double) total / m) : Integer.MAX_VALUE; // ⌈N/M⌉
        boolean pinOn = subjects.contains(Subject.PINYIN);
        boolean ciyuOn = subjects.contains(Subject.CITU);

        // 在约束内尽量分配剩余题数（轮询，保证均衡）
        int guard = 0;
        while (remaining > 0 && guard < 10000) {
            guard++;
            boolean added = false;
            for (String s : list) {
                if (remaining <= 0) break;
                if (!applyCaps || canAdd(s, counts, cap, pinOn, ciyuOn)) {
                    counts.put(s, counts.get(s) + 1);
                    remaining--;
                    added = true;
                }
            }
            if (!added) break; // 单科上限已用尽
        }
        // 约束用尽后若仍 remaining>0（如 4 科且 total=10，上限合计仅 9），不再强制补足到 total——
        // 严格遵守单科上限优先于"总数恰为 N"。引擎会循环重洗 schedule，故解锁所需的 N 次作答仍可达成
        // （题目会重复，绝不无题可出）。

        List<String> schedule = new ArrayList<>();
        for (String s : list) {
            int c = counts.get(s);
            for (int k = 0; k < c; k++) schedule.add(s);
        }
        Collections.shuffle(schedule, rnd);
        return schedule;
    }

    /** 单科上限判断：math/english 各自 < cap；拼音+语文在对方启用时二者合计 < cap，否则各自 < cap */
    private static boolean canAdd(@NonNull String s, @NonNull Map<String, Integer> counts,
                                  int cap, boolean pinOn, boolean ciyuOn) {
        if (Subject.MATH.equals(s)) return counts.get(s) < cap;
        if (Subject.ENGLISH.equals(s)) return counts.get(s) < cap;
        if (Subject.PINYIN.equals(s)) {
            if (ciyuOn) return (counts.get(Subject.PINYIN) + counts.get(Subject.CITU)) < cap;
            return counts.get(s) < cap;
        }
        if (Subject.CITU.equals(s)) {
            if (pinOn) return (counts.get(Subject.PINYIN) + counts.get(Subject.CITU)) < cap;
            return counts.get(s) < cap;
        }
        return true;
    }
}
