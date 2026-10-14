package com.kidslock.app.question;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 题库仓库：assets JSON 读取 + 进程内缓存 + 三级兜底（L1 同年级 / L2 相邻年级合并 / L3 内置 144 条）。
 *
 * <p>线程安全：{@link #get(Context)} 返回进程内单例；缓存为 ConcurrentHashMap。</p>
 *
 * <p>三级兜底（设计 §4.2）：
 * <ol>
 *   <li>L1 读 {@code banks/<subject>_<grade>.json}（org.json 解析，条目 ≥ 4 才可用），正常路径不打日志；</li>
 *   <li>L2 按距离顺序尝试相邻年级（g-1, g+1, g-2, g+2 … 在 0..6 内），首个可用者胜出，{@code Log.w}；</li>
 *   <li>L3 仅拼音：回退到 {@link BuiltinPinyinBank} 内置 144 条（编译进 dex 不可能缺失），{@code Log.e}。</li>
 * </ol>
 * 每一级失败/兜底都必须打日志（R13 ④），TAG 统一 {@value #TAG}。失败结果同样缓存，避免每题重复 IO（R11 ②）。
 *
 * <p>注意：首题解析发生在主线程（几百条 < 5 ms），结果立即缓存；若未来字表扩到数千条再考虑
 * {@code onCreate} 异步预热（本期不做，见设计 §9.8）。
 */
public final class BankRepository {
    private static final String TAG = "KidsLock-Question";
    private static final int MIN_ENTRIES = 4;

    private static volatile BankRepository instance;

    @NonNull
    private final Context appContext;
    @NonNull
    private final List<BankSource> sources;
    /** (subject_grade) -> LoadResult，含失败结果 */
    private final Map<String, LoadResult> cache = new ConcurrentHashMap<>();

    private BankRepository(@NonNull Context appContext) {
        this.appContext = appContext.getApplicationContext();
        List<BankSource> list = new ArrayList<>();
        list.add(new CustomBankSource(appContext));
        list.add(new AssetBankSource(appContext));
        list.add(new BuiltinBankSource());
        list.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        this.sources = list;
    }

    /** 进程内单例 */
    @NonNull
    public static BankRepository get(@NonNull Context ctx) {
        if (instance == null) {
            synchronized (BankRepository.class) {
                if (instance == null) {
                    instance = new BankRepository(ctx);
                }
            }
        }
        return instance;
    }

    /**
     * 按 (subject, grade) 加载题库，结果（含失败）按此键缓存，进程内只解析一次。
     */
    @NonNull
    public LoadResult load(@NonNull String subject, int grade) {
        String key = subject + "_" + grade;
        LoadResult cached = cache.get(key);
        if (cached != null) return cached;
        LoadResult result = loadInternal(subject, grade);
        cache.put(key, result);
        return result;
    }

    /** 清理缓存（测试用，正常路径无需调用） */
    public void clearCache() {
        cache.clear();
    }

    @NonNull
    private LoadResult loadInternal(@NonNull String subject, int grade) {
        for (BankSource source : sources) {
            LoadResult result = source.load(subject, grade);
            if (result != null && result.usable()) {
                return result;
            }
        }

        Log.w(TAG, "题库不可用: subject=" + subject + " 年级=" + grade);
        return new LoadResult(Collections.emptyList(), 0, new int[]{grade});
    }

    /** assets 路径拼接唯一入口：banks/<subject>_<grade>.json */
    @NonNull
    public static String assetPath(@NonNull String subject, int grade) {
        return "banks/" + subject + "_" + grade + ".json";
    }

    /**
     * 一次题库加载结果。
     * level：1=同年级，2=相邻年级合并，3=内置兜底，0=不可用。
     * gradesUsed：实际被使用的年级（L1/L3 为请求年级，L2 为回退年级）。
     */
    public static final class LoadResult {
        @NonNull
        public final List<BankEntry> entries;
        public final int level;
        @NonNull
        public final int[] gradesUsed;

        public LoadResult(@NonNull List<BankEntry> entries, int level, @NonNull int[] gradesUsed) {
            this.entries = entries;
            this.level = level;
            this.gradesUsed = gradesUsed;
        }

        /** 条目数 ≥ 4 才视为可用（PRD R12 ③） */
        public boolean usable() {
            return entries != null && entries.size() >= MIN_ENTRIES;
        }
    }
}
