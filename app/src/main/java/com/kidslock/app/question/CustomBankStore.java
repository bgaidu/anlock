package com.kidslock.app.question;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 家长自定义题库存储（R26，运行期导入，不进 assets）。
 *
 * <p>文件位于 {@code getFilesDir()/custom_banks/<subject>_<grade>.json}，schema 与 assets 题库一致
 * （{@code {"entries":[{"k":...,"v":...}, ...]}}）。导入时严格校验：科目仅限题库型
 * （拼音/英语/语文，MVP 不支持代码生成的数学）、条目 ≥ 4、每条 k/v 非空；失败返回 false，
 * <b>绝不抛异常</b>。</p>
 *
 * <p>{@link #loadCustom} 仅在文件存在且条目 ≥ 4 时返回可用结果，否则返回 null，由
 * BankRepository 透明回退 L1–L3，绝不破坏「永不 null」契约。写入时规范化输出，保证与 assets 同 schema。</p>
 */
public final class CustomBankStore {

    private static final String DIR = "custom_banks";
    private static final int MIN_ENTRIES = 4;
    // MVP 仅支持题库型科目（数学为代码生成，不支持导入）
    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            Subject.PINYIN, Subject.ENGLISH, Subject.CITU));

    private CustomBankStore() {
        // 工具类，禁止实例化
    }

    private static File fileFor(@NonNull Context ctx, @NonNull String subject, int grade) {
        File dir = new File(ctx.getFilesDir(), DIR);
        return new File(dir, subject + "_" + grade + ".json");
    }

    /** 该科目是否支持导入自定义题库（仅题库型） */
    public static boolean isSupported(@NonNull String subject) {
        return SUPPORTED.contains(subject);
    }

    public static boolean hasCustom(@NonNull Context ctx, @NonNull String subject, int grade) {
        return fileFor(ctx, subject, grade).exists();
    }

    /** 读取自定义题库；文件缺失/非法/不足 4 条 → 返回 null（交由上层兜底） */
    @Nullable
    public static BankRepository.LoadResult loadCustom(@NonNull Context ctx,
                                                      @NonNull String subject, int grade) {
        File f = fileFor(ctx, subject, grade);
        if (!f.exists()) return null;
        try (FileInputStream is = new FileInputStream(f)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            List<BankEntry> list = parseEntries(sb.toString());
            if (list == null || list.size() < MIN_ENTRIES) return null;
            return new BankRepository.LoadResult(list, 0, new int[]{grade}); // level=0 标记自定义
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 导入自定义题库：解析并校验 json，写入 files/custom_banks/<subject>_<grade>.json。
     * 校验失败（非 JSON / 缺 entries / 条目 < 4 / k 或 v 为空 / 科目不支持）→ 返回 false，绝不抛。
     */
    public static boolean importBank(@NonNull Context ctx, @NonNull String subject, int grade,
                                     @NonNull String json) {
        if (!isSupported(subject) || !Grade.isValid(grade)) return false;
        List<BankEntry> list = parseEntries(json);
        if (list == null || list.size() < MIN_ENTRIES) return false;

        // 规范化写入，保证与 assets schema 一致，便于 loadCustom 复用同一解析
        try {
            JSONObject out = new JSONObject();
            JSONArray outArr = new JSONArray();
            for (BankEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("k", e.k);
                o.put("v", e.v);
                outArr.put(o);
            }
            out.put("entries", outArr);
            File f = fileFor(ctx, subject, grade);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream os = new FileOutputStream(f)) {
                os.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (IOException | RuntimeException | org.json.JSONException e) {
            return false;
        }
    }

    /** 列出已导入的自定义题库键，格式 "subject_grade" */
    @NonNull
    public static List<String> listCustom(@NonNull Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR);
        List<String> out = new ArrayList<>();
        if (!dir.exists()) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".json")) {
                out.add(f.getName().replace(".json", ""));
            }
        }
        Collections.sort(out);
        return out;
    }

    /** 清除某 (subject, grade) 的自定义题库；文件不存在视为成功 */
    public static boolean clear(@NonNull Context ctx, @NonNull String subject, int grade) {
        File f = fileFor(ctx, subject, grade);
        if (!f.exists()) return true;
        return f.delete();
    }

    /**
     * 解析题库 JSON 为条目列表。支持 {@code {"entries":[...]}} 与顶层数组 {@code [{...}]} 两种写法。
     * 任一条目 k/v 为空 → 返回 null（整文件拒收，绝不静默降级）；非法 JSON → null。
     */
    @Nullable
    private static List<BankEntry> parseEntries(@NonNull String json) {
        try {
            List<BankEntry> list = new ArrayList<>();
            JSONArray arr;
            String t = json.trim();
            if (t.startsWith("[")) {
                arr = new JSONArray(t);
            } else {
                JSONObject obj = new JSONObject(t);
                arr = obj.optJSONArray("entries");
                if (arr == null) return null;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                String k = e.optString("k", "");
                String v = e.optString("v", "");
                if (k.isEmpty() || v.isEmpty()) return null; // 任一空字段 → 整文件拒收
                list.add(new BankEntry(k, v));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}
