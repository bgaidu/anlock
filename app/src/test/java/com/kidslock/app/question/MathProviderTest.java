package com.kidslock.app.question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * R23：数学 7 年级出题质量单测（JVM，MathProvider 传 null context 即可）。
 * 验证：选项恒 4、文本互异、干扰项 ≠ 正确项；整数题型干扰项与正确值位数差 ≤ 1。
 */
public class MathProviderTest {

    private static int digitCount(long n) {
        if (n <= 0) return 1;
        int c = 0;
        long x = n;
        while (x > 0) { c++; x /= 10; }
        return c;
    }

    /** 取整数部分位数（小数/分数按整数部分，对齐 MathSpec.magDigit 的简化版） */
    private static int magDigit(String text) {
        int dot = text.indexOf('.');
        String intPart = dot >= 0 ? text.substring(0, dot) : text;
        intPart = intPart.replace("-", "");
        if (intPart.isEmpty()) return 1;
        return digitCount(Long.parseLong(intPart));
    }

    @Test
    public void allGrades_produceValidOptions() {
        MathProvider p = new MathProvider(null);
        Random rnd = new Random(42);
        for (int grade = 0; grade <= 6; grade++) {
            for (int t = 0; t < 200; t++) {
                Question q = p.next(grade, rnd);
                assertNotNull("grade=" + grade + " 应能出题", q);
                assertEquals(4, q.options.size());
                Set<String> seen = new HashSet<>(q.options);
                assertEquals("选项应互异", 4, seen.size());
                String answer = q.options.get(q.correctIndex);
                for (int i = 0; i < q.options.size(); i++) {
                    if (i != q.correctIndex) {
                        assertTrue("干扰项不应等于正确项", !q.options.get(i).equals(answer));
                    }
                }
            }
        }
    }

    @Test
    public void integerQuestions_digitsWithinOne() {
        // 整数题型：干扰项与正确值位数差 <= 1
        MathProvider p = new MathProvider(null);
        Random rnd = new Random(7);
        int checked = 0;
        for (int grade = 0; grade <= 6 && checked < 50; grade++) {
            for (int t = 0; t < 300; t++) {
                Question q = p.next(grade, rnd);
                if (q == null) continue;
                String answer = q.options.get(q.correctIndex);
                if (!answer.matches("-?\\d+")) continue; // 仅整数题目
                int aMag = magDigit(answer);
                for (int i = 0; i < q.options.size(); i++) {
                    if (i == q.correctIndex) continue;
                    String d = q.options.get(i);
                    if (!d.matches("-?\\d+")) continue;
                    int dMag = magDigit(d);
                    assertTrue("位数差应<=1: " + answer + " vs " + d, Math.abs(dMag - aMag) <= 1);
                    checked++;
                }
                if (checked >= 50) break;
            }
        }
        assertTrue("应至少检查到若干整数题", checked > 0);
    }
}
