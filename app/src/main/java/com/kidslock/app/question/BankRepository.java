package com.kidslock.app.question;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    /** (subject_grade) -> LoadResult，含失败结果 */
    private final Map<String, LoadResult> cache = new ConcurrentHashMap<>();

    private BankRepository(@NonNull Context appContext) {
        this.appContext = appContext.getApplicationContext();
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
        // L1：同年级
        LoadResult l1 = tryLoadGrade(subject, grade);
        if (l1 != null && l1.usable()) {
            return l1; // 正常路径，不打日志
        }

        // L2：相邻年级按距离顺序（g-1, g+1, g-2, g+2 …），首个可用者胜出
        for (int cg : adjacentGrades(grade)) {
            if (cg == grade) continue;
            LoadResult r = tryLoadGrade(subject, cg);
            if (r != null && r.usable()) {
                Log.w(TAG, "题库L2回退: subject=" + subject
                        + " 原年级=" + grade + " 改用年级=" + cg);
                return new LoadResult(r.entries, 2, new int[]{cg});
            }
        }

        // L3：仅拼音回退内置 144 条（最后防线）
        if (Subject.PINYIN.equals(subject)) {
            List<BankEntry> builtin = BuiltinPinyinBank.entries();
            Log.e(TAG, "题库L3强制回退: subject=pinyin 原年级=" + grade
                    + " 使用内置" + builtin.size() + "条");
            return new LoadResult(builtin, 3, new int[]{grade});
        }

        // 非拼音且无可用资产：不可用
        Log.w(TAG, "题库不可用: subject=" + subject + " 年级=" + grade);
        return new LoadResult(Collections.emptyList(), 0, new int[]{grade});
    }

    @Nullable
    private LoadResult tryLoadGrade(@NonNull String subject, int grade) {
        String path = assetPath(subject, grade);
        try (InputStream is = appContext.getAssets().open(path)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            JSONArray arr = obj.optJSONArray("entries");
            if (arr == null) return new LoadResult(Collections.emptyList(), 1, new int[]{grade});
            List<BankEntry> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                String k = e.optString("k", "");
                String v = e.optString("v", "");
                if (k.isEmpty() || v.isEmpty()) continue;
                list.add(new BankEntry(k, v));
            }
            return new LoadResult(list, 1, new int[]{grade});
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            // 文件缺失 / JSON 非法：返回 null，交由上级兜底
            return null;
        }
    }

    /** 按距离顺序生成相邻年级候选（g-1, g+1, g-2, g+2 …），过滤越界，去重 */
    @NonNull
    private static List<Integer> adjacentGrades(int grade) {
        List<Integer> out = new ArrayList<>();
        for (int d = 1; d <= Grade.MAX; d++) {
            int lo = grade - d;
            int hi = grade + d;
            if (lo >= Grade.MIN && lo <= Grade.MAX) out.add(lo);
            if (hi >= Grade.MIN && hi <= Grade.MAX) out.add(hi);
        }
        return out;
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
