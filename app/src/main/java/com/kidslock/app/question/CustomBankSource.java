package com.kidslock.app.question;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CustomBankSource implements BankSource {
    private static final String TAG = "KidsLock-Question";

    @NonNull
    private final Context appContext;

    public CustomBankSource(@NonNull Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @Nullable
    @Override
    public BankRepository.LoadResult load(@NonNull String subject, int grade) {
        if (!CustomBankStore.hasCustom(appContext, subject, grade)) {
            return null;
        }
        BankRepository.LoadResult custom = CustomBankStore.loadCustom(appContext, subject, grade);
        if (custom != null && custom.usable()) {
            Log.i(TAG, "题库L0自定义命中: subject=" + subject + " 年级=" + grade);
            return custom;
        }
        return null;
    }

    @Override
    public int priority() {
        return 0;
    }
}
