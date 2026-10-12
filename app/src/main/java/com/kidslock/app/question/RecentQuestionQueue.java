package com.kidslock.app.question;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 跨科目统一防重复队列（设计 §9.3）。
 * 容量 10、FIFO 淘汰，与现状 CharacterBank.RECENT_MEMORY=10 数值与语义完全一致。
 * 队列由 {@link QuestionEngine} 持有，Activity 重建即清空（进程内实例持有）。
 */
public final class RecentQuestionQueue {
    private static final int CAPACITY = 10;

    private final Deque<String> recent = new ArrayDeque<>();

    /** 是否为最近出现的题目 ID */
    public boolean contains(@NonNull String id) {
        return recent.contains(id);
    }

    /** 入队；超过容量时淘汰最早一道 */
    public void offer(@NonNull String id) {
        if (recent.size() >= CAPACITY) recent.pollFirst();
        recent.addLast(id);
    }

    /** 清空（Activity 重建时调用，保持与现状语义一致） */
    public void clear() {
        recent.clear();
    }
}
