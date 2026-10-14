package com.kidslock.app.question;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * R23：干扰项采样单测（JVM 纯逻辑，无 Android 依赖）。
 * 覆盖拼音/英语正反向/语文方向，并验证「候选不足绝不占位」。
 */
public class DistractorSamplerTest {

    /** 由 (k, v, k, v, ...) 形参构造题库池 */
    private static List<BankEntry> pool(Object... kv) {
        List<BankEntry> list = new ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            list.add(new BankEntry((String) kv[i], (String) kv[i + 1]));
        }
        return list;
    }

    private static void assertValid4(List<String> options, String answer) {
        assertNotNull(options);
        assertEquals(4, options.size());
        Set<String> seen = new HashSet<>(options);
        assertEquals("选项文本应互异", 4, seen.size());
        assertTrue("选项应含正确项", options.contains(answer));
        for (String o : options) assertNotNull(o);
    }

    @Test
    public void pinyinForward_returns4Distinct() {
        // 拼音：v=拼音，answerKey=false → 选项取 v
        List<BankEntry> pool = pool("妈", "mā", "麻", "má", "马", "mǎ", "骂", "mà", "吗", "ma");
        Random rnd = new Random(1);
        for (int t = 0; t < 50; t++) {
            BankEntry chosen = pool.get(t % pool.size());
            List<String> opts = DistractorSampler.sample(pool, chosen, false, rnd);
            assertValid4(opts, chosen.v);
        }
    }

    @Test
    public void englishForward_usesV() {
        // 英语正向：k=单词 v=释义，answerKey=false → 选项取 v
        List<BankEntry> pool = pool("apple", "苹果", "banana", "香蕉", "cat", "猫", "dog", "狗");
        Random rnd = new Random(2);
        for (int t = 0; t < 50; t++) {
            BankEntry chosen = pool.get(t % pool.size());
            List<String> opts = DistractorSampler.sample(pool, chosen, false, rnd);
            assertValid4(opts, chosen.v);
        }
    }

    @Test
    public void englishReverse_usesK() {
        // 英语反向：answerKey=true → 选项取 k（单词）
        List<BankEntry> pool = pool("apple", "苹果", "banana", "香蕉", "cat", "猫", "dog", "狗");
        Random rnd = new Random(3);
        for (int t = 0; t < 50; t++) {
            BankEntry chosen = pool.get(t % pool.size());
            List<String> opts = DistractorSampler.sample(pool, chosen, true, rnd);
            assertValid4(opts, chosen.k);
        }
    }

    @Test
    public void ciyuForward_usesV() {
        // 语文：k=词语 v=拼音，answerKey=false → 选项取 v
        List<BankEntry> pool = pool("妈妈", "māma", "爸爸", "bàba", "哥哥", "gēge", "弟弟", "dìdi");
        Random rnd = new Random(4);
        for (int t = 0; t < 50; t++) {
            BankEntry chosen = pool.get(t % pool.size());
            List<String> opts = DistractorSampler.sample(pool, chosen, false, rnd);
            assertValid4(opts, chosen.v);
        }
    }

    @Test
    public void insufficientCandidates_returnsNull() {
        // 所有 v 相同 → 候选去重后 < 3 → null（绝不占位）
        List<BankEntry> pool = pool("妈", "mā", "麻", "mā", "马", "mā", "骂", "mā");
        Random rnd = new Random(5);
        List<String> opts = DistractorSampler.sample(pool, pool.get(0), false, rnd);
        assertNull(opts);
    }

    @Test
    public void distractorsNotEqualToCorrect() {
        List<BankEntry> pool = pool("a", "A", "b", "B", "c", "C", "d", "D", "e", "E");
        Random rnd = new Random(6);
        for (int t = 0; t < 100; t++) {
            BankEntry chosen = pool.get(t % pool.size());
            List<String> opts = DistractorSampler.sample(pool, chosen, false, rnd);
            assertValid4(opts, chosen.v);
            int correct = opts.indexOf(chosen.v);
            for (int i = 0; i < opts.size(); i++) {
                if (i != correct) {
                    assertTrue("干扰项不应等于正确项", !opts.get(i).equals(chosen.v));
                }
            }
        }
    }
}
