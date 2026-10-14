package com.kidslock.app.question;

import androidx.annotation.NonNull;

public interface BankSource {
    BankRepository.LoadResult load(@NonNull String subject, int grade);
    int priority();
}
