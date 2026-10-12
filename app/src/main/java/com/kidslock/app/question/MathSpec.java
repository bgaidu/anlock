package com.kidslock.app.question;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 数学出题参数表与生成器（设计 §3.4 / §9.4）。
 *
 * <p>核心约定：
 * <ul>
 *   <li>一律 {@code long} 定点整数：1 位小数存 ×10、2 位存 ×100；比较/去重/量级全在定点域；
 *       只在渲染时格式化。禁止用 double 参与相等判断。</li>
 *   <li>除法必须整除；学前~二年级不出负数与小数；结果整数或 ≤2 位有限小数。</li>
 *   <li>小数选项统一保留固定位数（不去除尾随零），否则位数泄露答案。</li>
 *   <li>分数线性文本 {@code n/d}，结果约分到最简；干扰项与正确值用交叉相乘判不等（4/8 与 1/2 同值异形被拒）。</li>
 *   <li>干扰项与正确值位数差 ≤ 1（整数按位数，小数按整数部分位数，分数按分子/分母）。</li>
 * </ul>
 */
public final class MathSpec {

    /** 题型枚举（对应设计 §3.4 各年级参数表） */
    public enum MathKind {
        ADD_SUB_10,        // 0 学前
        ADD_SUB_20,        // 1 一年级
        MUL_TABLE,         // 2 二年级 乘法口诀
        ADD_SUB_100,       // 2 二年级 百内加减
        MUL_DIV,           // 3 三年级 乘除
        TWO_STEP,          // 3 三年级 两步混合
        MIXED_PAREN,       // 4 四年级 带括号混合
        TWO_DIGIT_X_ONE,   // 4 四年级 两位数×一位数
        MIXED_TWO_STEP,    // 5 五年级 整数两步混合
        DECIMAL_ADD_SUB,   // 5 五年级 小数加减
        MIXED_THREE,       // 6 六年级 三步混合
        FRACTION_ADD_SUB,  // 6 六年级 分数加减
        DECIMAL_MUL_DIV    // 6 六年级 小数乘除
    }

    /** 答案数值形态（决定渲染与比较方式） */
    public enum Format { INT, DECIMAL, FRACTION }

    /** 一道数学题的生成结果 */
    public static final class MathProblem {
        @NonNull
        public final String stem;
        @NonNull
        public final String answerText;
        @NonNull
        public final List<String> distractorTexts; // 恰好 3 个，文本互异且数值不同于答案
        @NonNull
        public final Format format;
        public final int answerDigits;
        @NonNull
        public final String id;

        public MathProblem(@NonNull String stem, @NonNull String answerText,
                           @NonNull List<String> distractorTexts, @NonNull Format format,
                           int answerDigits, @NonNull String id) {
            this.stem = stem;
            this.answerText = answerText;
            this.distractorTexts = distractorTexts;
            this.format = format;
            this.answerDigits = answerDigits;
            this.id = id;
        }
    }

    /** 各年级题型表（索引 = 年级 0..6） */
    public static final MathKind[][] BY_GRADE = {
            {MathKind.ADD_SUB_10},                                  // 0 学前
            {MathKind.ADD_SUB_20},                                  // 1 一年级
            {MathKind.MUL_TABLE, MathKind.ADD_SUB_100},             // 2 二年级
            {MathKind.MUL_DIV, MathKind.TWO_STEP},                  // 3 三年级
            {MathKind.MIXED_PAREN, MathKind.TWO_DIGIT_X_ONE},       // 4 四年级
            {MathKind.MIXED_TWO_STEP, MathKind.DECIMAL_ADD_SUB},    // 5 五年级
            {MathKind.MIXED_THREE, MathKind.FRACTION_ADD_SUB, MathKind.DECIMAL_MUL_DIV} // 6 六年级
    };

    private MathSpec() {
        // 工具类
    }

    /**
     * 按题型生成一道题。失败时返回 null（干扰项凑不齐等），由 MathProvider 换题重抽。
     */
    @Nullable
    public static MathProblem generate(@NonNull MathKind kind, @NonNull Random random) {
        switch (kind) {
            case ADD_SUB_10:      return genAddSub10(random);
            case ADD_SUB_20:      return genAddSub20(random);
            case MUL_TABLE:       return genMulTable(random);
            case ADD_SUB_100:     return genAddSub100(random);
            case MUL_DIV:         return genMulDiv(random);
            case TWO_STEP:        return genTwoStep(random);
            case MIXED_PAREN:     return genMixedParen(random);
            case TWO_DIGIT_X_ONE: return genTwoDigitXOne(random);
            case MIXED_TWO_STEP:  return genMixedTwoStep(random);
            case DECIMAL_ADD_SUB: return genDecimalAddSub(random);
            case MIXED_THREE:     return genMixedThree(random);
            case FRACTION_ADD_SUB:return genFractionAddSub(random);
            case DECIMAL_MUL_DIV: return genDecimalMulDiv(random);
            default:              return null;
        }
    }

    // ===================== 生成器（各年级） =====================

    private static MathProblem genAddSub10(Random r) {
        int a = randInt(r, 1, 9), b = randInt(r, 1, 9);
        boolean sub = r.nextBoolean();
        long ans;
        String stem;
        if (sub) {
            if (a < b) { int t = a; a = b; b = t; }
            ans = a - b;
            stem = a + " - " + b + " = ?";
        } else {
            if (a + b > 10) a = randInt(r, 1, 10 - b);
            ans = a + b;
            stem = a + " + " + b + " = ?";
        }
        List<Long> c = new ArrayList<>();
        c.add(ans + 1);
        c.add(ans - 1);
        if (sub) c.add((long) (a + b));
        else if (a > b) c.add((long) (a - b));
        for (int i = 0; i < 8; i++) c.add(1L + r.nextInt(Math.max(1, (int) Math.min(99, ans * 2 + 2))));
        return build(stem, ans, false, 0, pick3(c, ans, false, 0));
    }

    private static MathProblem genAddSub20(Random r) {
        int a = randInt(r, 1, 19), b = randInt(r, 1, 19);
        boolean sub = r.nextBoolean();
        long ans;
        String stem;
        if (sub) {
            if (a < b) { int t = a; a = b; b = t; }
            ans = a - b;
            stem = a + " - " + b + " = ?";
        } else {
            if (a + b > 20) a = randInt(r, 1, 20 - b);
            ans = a + b;
            stem = a + " + " + b + " = ?";
        }
        List<Long> c = new ArrayList<>();
        c.add(ans + 1);
        c.add(ans - 1);
        c.add(ans + 10); // 进退位错 / ±10
        c.add(ans - 10);
        if (sub) c.add((long) (a + b));
        else if (a > b) c.add((long) (a - b));
        return build(stem, ans, false, 0, pick3(c, ans, false, 0));
    }

    private static MathProblem genMulTable(Random r) {
        int a = randInt(r, 2, 9), b = randInt(r, 2, 9);
        long ans = (long) a * b;
        String stem = a + " × " + b + " = ?";
        List<Long> c = new ArrayList<>();
        c.add(ans + 1);
        c.add(ans - 1);
        c.add((long) (a + 1) * b);
        c.add((long) (a - 1) * b);
        c.add((long) a * (b + 1));
        c.add((long) a * (b - 1));
        c.add(ans + 10);
        c.add(ans - 10);
        c.add((long) a + b);
        if (a > b) c.add((long) (a - b));
        return build(stem, ans, false, 0, pick3(c, ans, false, 0));
    }

    private static MathProblem genAddSub100(Random r) {
        int a = randInt(r, 1, 99), b = randInt(r, 1, 99);
        boolean sub = r.nextBoolean();
        long ans;
        String stem;
        if (sub) {
            if (a < b) { int t = a; a = b; b = t; }
            ans = a - b;
            stem = a + " - " + b + " = ?";
        } else {
            ans = a + b;
            stem = a + " + " + b + " = ?";
        }
        List<Long> c = new ArrayList<>();
        c.add(ans + 1);
        c.add(ans - 1);
        c.add(ans + 10); // 口诀邻位 / ±10
        c.add(ans - 10);
        if (sub) c.add((long) (a + b));
        else if (a > b) c.add((long) (a - b));
        for (int i = 0; i < 6; i++) c.add(1L + r.nextInt(Math.min(99, (int) (ans * 2 + 2))));
        return build(stem, ans, false, 0, pick3(c, ans, false, 0));
    }

    private static MathProblem genMulDiv(Random r) {
        if (r.nextBoolean()) {
            int a = randInt(r, 2, 12), b = randInt(r, 2, 12);
            long ans = (long) a * b;
            String stem = a + " × " + b + " = ?";
            List<Long> c = new ArrayList<>();
            c.add(ans + 1);
            c.add(ans - 1);
            c.add((long) (a + 1) * b);
            c.add((long) a * (b + 1));
            c.add((long) a * (b - 1));
            c.add(ans + 10);
            c.add(ans - 10);
            return build(stem, ans, false, 0, pick3(c, ans, false, 0));
        } else {
            int d = randInt(r, 2, 12);
            int q = randInt(r, 1, 12); // 被除数 = d*q ≤ 144，整除
            int dividend = d * q;
            long ans = q;
            String stem = dividend + " ÷ " + d + " = ?";
            List<Long> c = new ArrayList<>();
            c.add(ans + 1);
            c.add(ans - 1);
            if (d - 1 >= 2) c.add((long) (dividend / (d - 1))); // 运算顺序错
            c.add((long) ((dividend + d) / d));
            c.add(ans + 10);
            c.add(ans - 10);
            return build(stem, ans, false, 0, pick3(c, ans, false, 0));
        }
    }

    private static MathProblem genTwoStep(Random r) {
        int a = randInt(r, 1, 40), b = randInt(r, 1, 40), c = randInt(r, 1, 40);
        int op1 = r.nextBoolean() ? 1 : -1;
        int op2 = r.nextBoolean() ? 1 : -1;
        int ab = a + op1 * b;
        if (ab < 1 || ab > 100) { op1 = (ab < 1) ? 1 : -1; ab = a + op1 * b; }
        long ans = ab + op2 * c;
        if (ans < 1 || ans > 100) { op2 = (ans < 1) ? 1 : -1; ans = ab + op2 * c; }
        String stem = "(" + a + (op1 > 0 ? " + " : " - ") + b + ")" + (op2 > 0 ? " + " : " - ") + c + " = ?";
        List<Long> cands = new ArrayList<>();
        cands.add(ans + 1);
        cands.add(ans - 1);
        long wrongOrder = a + op1 * (b + op2 * c); // 运算顺序错：先算后一项
        if (wrongOrder >= 1) cands.add(wrongOrder);
        cands.add(ans + 10);
        cands.add(ans - 10);
        for (int i = 0; i < 6; i++) cands.add(1L + r.nextInt(Math.min(99, (int) (Math.abs(ans) * 2 + 2))));
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0));
    }

    private static MathProblem genMixedParen(Random r) {
        long ans;
        String stem;
        List<Long> cands = new ArrayList<>();
        if (r.nextBoolean()) {
            int a = randInt(r, 2, 20), b = randInt(r, 1, 40), c = randInt(r, 1, 40);
            if (b + c > 100) b = randInt(r, 1, Math.max(1, 50 - c));
            ans = (long) a * (b + c);
            stem = a + " × (" + b + " + " + c + ") = ?";
            cands.add((long) a * b + c); // 运算顺序错：忽略括号
        } else {
            int a = randInt(r, 1, 40), b = randInt(r, 1, 40), c = randInt(r, 2, 20);
            if (a + b > 100) a = randInt(r, 1, Math.max(1, 50 - b));
            ans = (long) (a + b) * c;
            stem = "(" + a + " + " + b + ") × " + c + " = ?";
            cands.add((long) a + (long) b * c); // 运算顺序错：忽略括号
        }
        cands.add(ans + 1);
        cands.add(ans - 1);
        long pv = placeValueError(ans); // 位值错：23×4 给 82
        if (pv > 0) cands.add(pv);
        cands.add(ans + 10);
        cands.add(ans - 10);
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0));
    }

    private static MathProblem genTwoDigitXOne(Random r) {
        int ab = randInt(r, 10, 99);
        int c = randInt(r, 2, 9);
        long ans = (long) ab * c;
        String stem = ab + " × " + c + " = ?";
        List<Long> cands = new ArrayList<>();
        cands.add((long) (ab + 1) * c); // 邻位积
        cands.add((long) (ab - 1) * c);
        cands.add(ans + 1);
        cands.add(ans - 1);
        long pv = (long) (ab / 10 * 10) * c + ((ab % 10) * c) % 10; // 位值错：23×4 → 82
        if (pv > 0 && pv != ans) cands.add(pv);
        cands.add(ans + 10);
        cands.add(ans - 10);
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0));
    }

    private static MathProblem genMixedTwoStep(Random r) {
        long ans;
        String stem;
        List<Long> cands = new ArrayList<>();
        if (r.nextBoolean()) {
            int a = randInt(r, 2, 12), b = randInt(r, 2, 12), c = randInt(r, 1, 30);
            ans = (long) a * b - c;
            if (ans < 1) { c = randInt(r, 1, (int) (a * b - 1)); ans = (long) a * b - c; }
            stem = a + " × " + b + " - " + c + " = ?";
            long wrong = (long) a * (b - c);
            if (wrong >= 1) cands.add(wrong);
        } else {
            int a = randInt(r, 1, 40), b = randInt(r, 2, 12), c = randInt(r, 1, 40);
            ans = (long) a + (long) b * c;
            if (ans > 200) { b = randInt(r, 2, Math.max(2, (200 - a) / c)); ans = a + (long) b * c; }
            stem = a + " + " + b + " × " + c + " = ?";
            cands.add((long) (a + b) * c); // 顺序错
        }
        cands.add(ans + 1);
        cands.add(ans - 1);
        cands.add(ans + 10);
        cands.add(ans - 10);
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0));
    }

    private static MathProblem genDecimalAddSub(Random r) {
        int digits = 1;
        long scale = 10;
        int a = randInt(r, 1, 90); // 定点 ×10
        int b = randInt(r, 1, 90);
        boolean sub = r.nextBoolean();
        long ansFp;
        String stem;
        if (sub) {
            if (a < b) { int t = a; a = b; b = t; }
            ansFp = a - b;
            if (ansFp < 1) { a = b + randInt(r, 1, 90); ansFp = a - b; } // 保证结果 ≥ 0.1
            stem = fmtDecimal(a, digits) + " - " + fmtDecimal(b, digits) + " = ?";
        } else {
            ansFp = a + b;
            if (ansFp > 990) { a = randInt(r, 1, Math.max(1, 990 - b)); ansFp = a + b; }
            stem = fmtDecimal(a, digits) + " + " + fmtDecimal(b, digits) + " = ?";
        }
        List<Long> cands = new ArrayList<>();
        cands.add(ansFp * 10);              // 小数点错位
        cands.add(ansFp + scale);          // ±1
        cands.add(ansFp - scale);
        cands.add(ansFp + 1);              // ±0.1
        cands.add(ansFp - 1);
        if (sub) cands.add((long) (a + b)); // 顺序错
        else if (a > b) cands.add((long) (a - b));
        return buildDecimal(stem, ansFp, digits, pick3(cands, ansFp, true, digits));
    }

    private static MathProblem genMixedThree(Random r) {
        int a = randInt(r, 1, 30), b = randInt(r, 2, 12), c = randInt(r, 2, 12), d = randInt(r, 1, 30);
        long ans = a + (long) b * c - d;
        if (ans < 1) { d = randInt(r, 1, (int) (a + (long) b * c - 1)); ans = a + (long) b * c - d; }
        String stem = a + " + " + b + " × " + c + " - " + d + " = ?";
        List<Long> cands = new ArrayList<>();
        long wrong1 = (long) (a + b) * c - d; // 顺序错
        long wrong2 = a + (long) b * (c - d);
        if (wrong1 >= 1) cands.add(wrong1);
        if (wrong2 >= 1) cands.add(wrong2);
        cands.add(ans + 1);
        cands.add(ans - 1);
        cands.add(ans + 10);
        cands.add(ans - 10);
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0));
    }

    private static MathProblem genFractionAddSub(Random r) {
        int d1 = randInt(r, 2, 12), d2 = randInt(r, 2, 12);
        int n1 = randInt(r, 1, d1);
        int n2 = randInt(r, 1, d2);
        boolean sub = r.nextBoolean();
        long[] ans = reduce(sub ? (long) n1 * d2 - (long) n2 * d1 : (long) n1 * d2 + (long) n2 * d1,
                (long) d1 * d2);
        if (ans == null || ans[0] < 1) {
            n1 = randInt(r, 1, d1);
            n2 = randInt(r, 1, d2);
            sub = false;
            ans = reduce((long) n1 * d2 + (long) n2 * d1, (long) d1 * d2);
        }
        String stem = n1 + "/" + d1 + (sub ? " - " : " + ") + n2 + "/" + d2 + " = ?";
        List<long[]> cands = new ArrayList<>();
        cands.add(reduce(n1 + n2, d1 + d2));   // ① 通分错：分母直接相加
        cands.add(reduce(n1 + 1, d1));          // ③ ±1（分子/分母邻位）
        cands.add(reduce(Math.max(1, n1 - 1), d1));
        cands.add(reduce(n1, d1 + 1));
        cands.add(reduce(n1, Math.max(2, d1 - 1)));
        if (sub) cands.add(reduce((long) n1 * d2 + (long) n2 * d1, (long) d1 * d2)); // ④ 顺序错
        for (int i = 2; i <= 12; i++) {
            cands.add(reduce(n1, i));
            cands.add(reduce(i, d1));
        }
        return buildFrac(stem, ans, pick3Frac(cands, ans));
    }

    private static MathProblem genDecimalMulDiv(Random r) {
        long scale = 10; // 1 位小数操作数
        if (r.nextBoolean()) {
            // 乘法：a.b × c.d（结果 ≤ 2 位小数）
            long a = randInt(r, 1, 90);
            long c = randInt(r, 1, 90);
            long ansFp = a * c; // 值 = ansFp/100，固定 2 位小数
            int ansDigits = 2;
            String stem = fmtDecimal(a, 1) + " × " + fmtDecimal(c, 1) + " = ?";
            List<Long> cands = new ArrayList<>();
            cands.add(ansFp * 10);                 // 小数点错位
            cands.add(ansFp + scale * scale);      // ±1
            cands.add(ansFp - scale * scale);
            cands.add(ansFp + scale);              // ±0.1
            cands.add(ansFp - scale);
            return buildDecimal(stem, ansFp, ansDigits, pick3(cands, ansFp, true, ansDigits));
        } else {
            // 除法：dividend ÷ divisor = r（整除，结果 ≤ 1 位小数）
            long divisor = randInt(r, 1, 90);
            long rFp = randInt(r, 1, 90);
            long dividend = rFp * divisor; // 2 位小数定点
            int ansDigits = 1;
            long ansFp = rFp;
            String stem = fmtDecimal(dividend, 2) + " ÷ " + fmtDecimal(divisor, 1) + " = ?";
            List<Long> cands = new ArrayList<>();
            cands.add(ansFp * 10);        // 小数点错位
            cands.add(ansFp + scale);     // ±1
            cands.add(ansFp - scale);
            cands.add(ansFp + 1);         // ±0.1
            cands.add(ansFp - 1);
            return buildDecimal(stem, ansFp, ansDigits, pick3(cands, ansFp, true, ansDigits));
        }
    }

    // ===================== 公共工具 =====================

    private static int randInt(Random r, int lo, int hi) {
        if (hi < lo) { int t = lo; lo = hi; hi = t; }
        return lo + r.nextInt(hi - lo + 1);
    }

    private static long pow10(int n) {
        long r = 1;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }

    private static int digitCount(long n) {
        if (n == 0) return 1;
        int c = 0;
        long x = Math.abs(n);
        while (x > 0) { c++; x /= 10; }
        return c;
    }

    /** 固定位数渲染小数（不去除尾随零） */
    private static String fmtDecimal(long fp, int digits) {
        StringBuilder sb = new StringBuilder();
        if (fp < 0) { sb.append('-'); fp = -fp; }
        long scale = pow10(digits);
        long ip = fp / scale;
        long frac = fp % scale;
        sb.append(ip);
        if (digits > 0) {
            sb.append('.');
            String f = String.valueOf(frac);
            while (f.length() < digits) f = "0" + f;
            sb.append(f);
        }
        return sb.toString();
    }

    /** 整数/小数的量级位数（整数按位数，小数按整数部分位数） */
    private static int magDigit(long fp, boolean isDecimal, int digits) {
        if (!isDecimal) return digitCount(Math.abs(fp));
        long scale = pow10(digits);
        return digitCount(Math.abs(fp) / scale);
    }

    /** 位值错：最高位降一档（23×4=92 → 82） */
    private static long placeValueError(long ans) {
        int dc = digitCount(ans);
        if (dc < 2) return -1;
        long p = pow10(dc - 1);
        return ans - p;
    }

    /** 约分（返回 [num, den] 最简；非法返回 null） */
    @Nullable
    private static long[] reduce(long num, long den) {
        if (den <= 0) return null;
        if (num <= 0) return new long[]{num, den}; // 调用方过滤 ≤0
        long g = gcd(Math.abs(num), den);
        return new long[]{num / g, den / g};
    }

    private static long gcd(long a, long b) {
        while (b != 0) { long t = b; b = a % b; a = t; }
        return a;
    }

    private static boolean fracEqual(long[] x, long[] y) {
        return x[0] * y[1] == y[0] * x[1];
    }

    /** 从候选长整型值中筛选 3 个合法干扰项（文本互异、数值不同于答案、位数差 ≤ 1、无 0/负） */
    private static List<String> select3(List<Long> cands, long answerVal, boolean isDecimal, int digits) {
        Set<String> used = new HashSet<>();
        List<String> out = new ArrayList<>();
        int dc = magDigit(answerVal, isDecimal, digits);
        for (Long c : cands) {
            if (out.size() >= 3) break;
            if (c == null) continue;
            if (c <= 0) continue;                 // 无 0/负数凑数
            if (c == answerVal) continue;         // 数值不能等于答案
            int cdc = magDigit(c, isDecimal, digits);
            if (Math.abs(cdc - dc) > 1) continue; // 位数差 ≤ 1
            String t = isDecimal ? fmtDecimal(c, digits) : Long.toString(c);
            if (used.add(t)) out.add(t);
        }
        return out;
    }

    /** select3 + 兜底填充（同量级典型错误扰动，非占位），保证凑满 3 个 */
    private static List<String> pick3(List<Long> cands, long answerVal, boolean isDecimal, int digits) {
        List<String> out = select3(cands, answerVal, isDecimal, digits);
        if (out.size() >= 3) return out;
        List<Long> extra = new ArrayList<>(cands);
        for (int d = 1; d <= 9; d++) { extra.add(answerVal + d); extra.add(answerVal - d); }
        for (int d = 10; d <= 90; d += 10) { extra.add(answerVal + d); extra.add(answerVal - d); }
        return select3(extra, answerVal, isDecimal, digits);
    }

    /** 分数干扰项筛选：分子分母均约分、文本互异、交叉相乘不等、位数差 ≤ 1 */
    private static List<String> select3Frac(List<long[]> cands, long[] answer) {
        Set<String> used = new HashSet<>();
        List<String> out = new ArrayList<>();
        int ndc = digitCount(answer[0]);
        int ddc = digitCount(answer[1]);
        for (long[] c : cands) {
            if (out.size() >= 3) break;
            if (c == null) continue;
            if (c[0] <= 0 || c[1] <= 0) continue;
            if (fracEqual(c, answer)) continue;
            if (Math.abs(digitCount(c[0]) - ndc) > 1) continue;
            if (Math.abs(digitCount(c[1]) - ddc) > 1) continue;
            String t = c[0] + "/" + c[1];
            if (used.add(t)) out.add(t);
        }
        return out;
    }

    private static List<String> pick3Frac(List<long[]> cands, long[] answer) {
        List<String> out = select3Frac(cands, answer);
        if (out.size() >= 3) return out;
        List<long[]> extra = new ArrayList<>(cands);
        for (int i = 1; i <= 9; i++) {
            extra.add(reduce(answer[0] + i, answer[1]));
            extra.add(reduce(Math.max(1, answer[0] - i), answer[1]));
            extra.add(reduce(answer[0], answer[1] + i));
        }
        return select3Frac(extra, answer);
    }

    @Nullable
    private static MathProblem build(String stem, long answerVal, boolean isDecimal, int digits,
                                     List<String> distractors) {
        if (distractors == null || distractors.size() != 3) return null;
        String answerText = isDecimal ? fmtDecimal(answerVal, digits) : Long.toString(answerVal);
        String id = "math:" + stem.replace("?", "").trim() + answerText;
        return new MathProblem(stem, answerText, distractors,
                isDecimal ? Format.DECIMAL : Format.INT, digits, id);
    }

    @Nullable
    private static MathProblem buildDecimal(String stem, long answerFp, int digits, List<String> distractors) {
        if (distractors == null || distractors.size() != 3) return null;
        String answerText = fmtDecimal(answerFp, digits);
        String id = "math:" + stem.replace("?", "").trim() + answerText;
        return new MathProblem(stem, answerText, distractors, Format.DECIMAL, digits, id);
    }

    @Nullable
    private static MathProblem buildFrac(String stem, long[] answer, List<String> distractors) {
        if (distractors == null || distractors.size() != 3) return null;
        String answerText = answer[0] + "/" + answer[1];
        String id = "math:" + stem.replace("?", "").trim() + answerText;
        return new MathProblem(stem, answerText, distractors, Format.FRACTION, 0, id);
    }
}
