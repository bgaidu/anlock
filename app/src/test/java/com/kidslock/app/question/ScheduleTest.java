package com.kidslock.app.question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * R23：科目顺序表不变量单测（镜像 QuestionEngine.buildSchedule 的约束）。
 * 验证：每科目 ≥ 1；N>5 时单科上限 ⌈N/M⌉（数学 / 英语 / 拼音+语文合计）永不突破。
 */
public class ScheduleTest {

    private static int cap(int total, int m) {
        return (int) Math.ceil((double) total / m);
    }

    private static int count(List<String> sched, String s) {
        int c = 0;
        for (String x : sched) if (s.equals(x)) c++;
        return c;
    }

    @Test
    public void everySubjectAtLeastOne() {
        // 题数足够（>= 科目数）时，每科目至少 1 题
        Set<String> subs = new LinkedHashSet<>(Arrays.asList("pinyin", "math", "english", "ciyu"));
        Random rnd = new Random(1);
        for (int total = 4; total <= 30; total++) {
            List<String> sched = QuestionEngine.buildSchedule(subs, total, rnd);
            for (String s : subs) {
                assertTrue(s + " 应至少出现 1 次", count(sched, s) >= 1);
            }
        }
    }

    @Test
    public void degenerateTotalFewerThanSubjects() {
        // 题数 < 科目数(t=1..3) 时：仅前 t 个科目各得 1 题（按 Subject.ORDER），
        // 其余科目 0 题——题数不足以每人一题时的确定性退化行为。
        Set<String> subs = new LinkedHashSet<>(Arrays.asList("pinyin", "math", "english", "ciyu"));
        Random rnd = new Random(1);
        for (int total = 1; total <= 3; total++) {
            List<String> sched = QuestionEngine.buildSchedule(subs, total, rnd);
            int nonEmpty = 0;
            for (String s : subs) if (count(sched, s) >= 1) nonEmpty++;
            assertEquals("恰有前 " + total + " 个科目出题", total, nonEmpty);
            // 出题总数 == total，且不重复越界
            assertEquals(total, sched.size());
        }
    }

    @Test
    public void capsNotExceededWhenTotalGreaterThan5() {
        // 4 科，total=10 → cap=ceil(10/4)=3
        Set<String> subs = new LinkedHashSet<>(Arrays.asList("pinyin", "math", "english", "ciyu"));
        int total = 10;
        int c = cap(total, 4); // 3
        Random rnd = new Random(2);
        for (int trial = 0; trial < 50; trial++) {
            List<String> sched = QuestionEngine.buildSchedule(subs, total, rnd);
            assertTrue("math <= cap(" + c + ")，实际=" + count(sched, "math"),
                    count(sched, "math") <= c);
            assertTrue("english <= cap(" + c + ")，实际=" + count(sched, "english"),
                    count(sched, "english") <= c);
            assertTrue("pinyin+ciyu <= cap(" + c + ")，实际=" + (count(sched, "pinyin") + count(sched, "ciyu")),
                    count(sched, "pinyin") + count(sched, "ciyu") <= c);
        }
    }

    @Test
    public void total5_noCapsButEachAtLeastOne() {
        // N==5 不设单科上限，但每科仍 ≥ 1，且总数恰为 5
        Set<String> subs = new LinkedHashSet<>(Arrays.asList("pinyin", "math", "english", "ciyu"));
        Random rnd = new Random(3);
        for (int trial = 0; trial < 20; trial++) {
            List<String> sched = QuestionEngine.buildSchedule(subs, 5, rnd);
            assertEquals(5, sched.size());
            for (String s : subs) assertTrue(s + " 应至少出现 1 次", count(sched, s) >= 1);
        }
    }

    @Test
    public void singleSubject_allQuestions() {
        // 仅 1 科 pinyin，total=10 → 全部 10 题都应是 pinyin
        Set<String> subs = new LinkedHashSet<>(Arrays.asList("pinyin"));
        Random rnd = new Random(4);
        List<String> sched = QuestionEngine.buildSchedule(subs, 10, rnd);
        assertEquals(10, sched.size());
        for (String x : sched) assertEquals("pinyin", x);
    }
}
