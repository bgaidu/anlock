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

public final class AssetBankSource implements BankSource {
    private static final String TAG = "KidsLock-Question";

    @NonNull
    private final Context appContext;

    public AssetBankSource(@NonNull Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @Nullable
    @Override
    public BankRepository.LoadResult load(@NonNull String subject, int grade) {
        BankRepository.LoadResult l1 = tryLoadGrade(subject, grade);
        if (l1 != null && l1.usable()) {
            return l1;
        }

        for (int cg : adjacentGrades(grade)) {
            if (cg == grade) continue;
            BankRepository.LoadResult r = tryLoadGrade(subject, cg);
            if (r != null && r.usable()) {
                Log.w(TAG, "题库L2回退: subject=" + subject
                        + " 原年级=" + grade + " 改用年级=" + cg);
                return new BankRepository.LoadResult(r.entries, 2, new int[]{cg});
            }
        }

        return null;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Nullable
    private BankRepository.LoadResult tryLoadGrade(@NonNull String subject, int grade) {
        String path = BankRepository.assetPath(subject, grade);
        try (InputStream is = appContext.getAssets().open(path)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            JSONArray arr = obj.optJSONArray("entries");
            if (arr == null) return new BankRepository.LoadResult(Collections.emptyList(), 1, new int[]{grade});
            List<BankEntry> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.getJSONObject(i);
                String k = e.optString("k", "");
                String v = e.optString("v", "");
                if (k.isEmpty() || v.isEmpty()) continue;
                list.add(new BankEntry(k, v));
            }
            return new BankRepository.LoadResult(list, 1, new int[]{grade});
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            return null;
        }
    }

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
}
