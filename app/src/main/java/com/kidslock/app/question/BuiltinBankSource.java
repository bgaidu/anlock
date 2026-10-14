package com.kidslock.app.question;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class BuiltinBankSource implements BankSource {
    private static final String TAG = "KidsLock-Question";

    @Nullable
    @Override
    public BankRepository.LoadResult load(@NonNull String subject, int grade) {
        if (!Subject.PINYIN.equals(subject)) {
            return null;
        }
        List<BankEntry> builtin = BuiltinPinyinBank.entries();
        Log.e(TAG, "题库L3强制回退: subject=pinyin 原年级=" + grade
                + " 使用内置" + builtin.size() + "条");
        return new BankRepository.LoadResult(builtin, 3, new int[]{grade});
    }

    @Override
    public int priority() {
        return 3;
    }
}
