package com.kidslock.app.question

import java.util.Random
import kotlin.math.abs
import kotlin.math.min

object MathSpec {

    enum class MathKind {
        ADD_SUB_10,
        ADD_SUB_20,
        MUL_TABLE,
        ADD_SUB_100,
        MUL_DIV,
        TWO_STEP,
        MIXED_PAREN,
        TWO_DIGIT_X_ONE,
        MIXED_TWO_STEP,
        DECIMAL_ADD_SUB,
        MIXED_THREE,
        FRACTION_ADD_SUB,
        DECIMAL_MUL_DIV,
        UNIT_CONVERT,
        NUMBER_PATTERN,
        WORD_PROBLEM
    }

    enum class Format { INT, DECIMAL, FRACTION }

    data class MathProblem(
        val stem: String,
        val answerText: String,
        val distractorTexts: List<String>,
        val format: Format,
        val answerDigits: Int,
        val id: String
    )

    @JvmField
    val BY_GRADE: Array<Array<MathKind>> = arrayOf(
        arrayOf(MathKind.ADD_SUB_10),
        arrayOf(MathKind.ADD_SUB_20),
        arrayOf(MathKind.MUL_TABLE, MathKind.ADD_SUB_100, MathKind.UNIT_CONVERT),
        arrayOf(
            MathKind.MUL_DIV, MathKind.TWO_STEP, MathKind.UNIT_CONVERT,
            MathKind.NUMBER_PATTERN, MathKind.WORD_PROBLEM
        ),
        arrayOf(
            MathKind.MIXED_PAREN, MathKind.TWO_DIGIT_X_ONE,
            MathKind.NUMBER_PATTERN, MathKind.WORD_PROBLEM
        ),
        arrayOf(
            MathKind.MIXED_TWO_STEP, MathKind.DECIMAL_ADD_SUB,
            MathKind.UNIT_CONVERT, MathKind.WORD_PROBLEM
        ),
        arrayOf(
            MathKind.MIXED_THREE, MathKind.FRACTION_ADD_SUB, MathKind.DECIMAL_MUL_DIV,
            MathKind.NUMBER_PATTERN, MathKind.WORD_PROBLEM
        )
    )

    @JvmStatic
    fun generate(kind: MathKind, random: Random): MathProblem? {
        return when (kind) {
            MathKind.ADD_SUB_10 -> genAddSub10(random)
            MathKind.ADD_SUB_20 -> genAddSub20(random)
            MathKind.MUL_TABLE -> genMulTable(random)
            MathKind.ADD_SUB_100 -> genAddSub100(random)
            MathKind.MUL_DIV -> genMulDiv(random)
            MathKind.TWO_STEP -> genTwoStep(random)
            MathKind.MIXED_PAREN -> genMixedParen(random)
            MathKind.TWO_DIGIT_X_ONE -> genTwoDigitXOne(random)
            MathKind.MIXED_TWO_STEP -> genMixedTwoStep(random)
            MathKind.DECIMAL_ADD_SUB -> genDecimalAddSub(random)
            MathKind.MIXED_THREE -> genMixedThree(random)
            MathKind.FRACTION_ADD_SUB -> genFractionAddSub(random)
            MathKind.DECIMAL_MUL_DIV -> genDecimalMulDiv(random)
            MathKind.UNIT_CONVERT -> genUnitConvert(random)
            MathKind.NUMBER_PATTERN -> genNumberPattern(random)
            MathKind.WORD_PROBLEM -> genWordProblem(random)
        }
    }

    private fun genAddSub10(r: Random): MathProblem? {
        var a = randInt(r, 1, 9); var b = randInt(r, 1, 9)
        val sub = r.nextBoolean()
        val ans: Long
        val stem: String
        if (sub) {
            if (a < b) { val t = a; a = b; b = t }
            ans = (a - b).toLong()
            stem = "$a - $b = ?"
        } else {
            if (a + b > 10) a = randInt(r, 1, 10 - b)
            ans = (a + b).toLong()
            stem = "$a + $b = ?"
        }
        val c = ArrayList<Long>()
        c.add(ans + 1)
        c.add(ans - 1)
        if (sub) c.add((a + b).toLong())
        else if (a > b) c.add((a - b).toLong())
        for (i in 0 until 8) c.add(1L + r.nextInt(maxOf(1, min(99, (ans * 2 + 2).toInt()))))
        return build(stem, ans, false, 0, pick3(c, ans, false, 0))
    }

    private fun genAddSub20(r: Random): MathProblem? {
        var a = randInt(r, 1, 19); var b = randInt(r, 1, 19)
        val sub = r.nextBoolean()
        val ans: Long
        val stem: String
        if (sub) {
            if (a < b) { val t = a; a = b; b = t }
            ans = (a - b).toLong()
            stem = "$a - $b = ?"
        } else {
            if (a + b > 20) a = randInt(r, 1, 20 - b)
            ans = (a + b).toLong()
            stem = "$a + $b = ?"
        }
        val c = ArrayList<Long>()
        c.add(ans + 1)
        c.add(ans - 1)
        c.add(ans + 10)
        c.add(ans - 10)
        if (sub) c.add((a + b).toLong())
        else if (a > b) c.add((a - b).toLong())
        return build(stem, ans, false, 0, pick3(c, ans, false, 0))
    }

    private fun genMulTable(r: Random): MathProblem? {
        val a = randInt(r, 2, 9); val b = randInt(r, 2, 9)
        val ans = (a * b).toLong()
        val stem = "$a × $b = ?"
        val c = ArrayList<Long>()
        c.add(ans + 1)
        c.add(ans - 1)
        c.add((a + 1) * b.toLong())
        c.add((a - 1) * b.toLong())
        c.add(a * (b + 1).toLong())
        c.add(a * (b - 1).toLong())
        c.add(ans + 10)
        c.add(ans - 10)
        c.add((a + b).toLong())
        if (a > b) c.add((a - b).toLong())
        return build(stem, ans, false, 0, pick3(c, ans, false, 0))
    }

    private fun genAddSub100(r: Random): MathProblem? {
        var a = randInt(r, 1, 99); var b = randInt(r, 1, 99)
        val sub = r.nextBoolean()
        val ans: Long
        val stem: String
        if (sub) {
            if (a < b) { val t = a; a = b; b = t }
            ans = (a - b).toLong()
            stem = "$a - $b = ?"
        } else {
            ans = (a + b).toLong()
            stem = "$a + $b = ?"
        }
        val c = ArrayList<Long>()
        c.add(ans + 1)
        c.add(ans - 1)
        c.add(ans + 10)
        c.add(ans - 10)
        if (sub) c.add((a + b).toLong())
        else if (a > b) c.add((a - b).toLong())
        for (i in 0 until 6) c.add(1L + r.nextInt(min(99, (ans * 2 + 2).toInt())))
        return build(stem, ans, false, 0, pick3(c, ans, false, 0))
    }

    private fun genMulDiv(r: Random): MathProblem? {
        if (r.nextBoolean()) {
            val a = randInt(r, 2, 12); val b = randInt(r, 2, 12)
            val ans = (a * b).toLong()
            val stem = "$a × $b = ?"
            val c = ArrayList<Long>()
            c.add(ans + 1)
            c.add(ans - 1)
            c.add((a + 1) * b.toLong())
            c.add(a * (b + 1).toLong())
            c.add(a * (b - 1).toLong())
            c.add(ans + 10)
            c.add(ans - 10)
            return build(stem, ans, false, 0, pick3(c, ans, false, 0))
        } else {
            val d = randInt(r, 2, 12)
            val q = randInt(r, 1, 12)
            val dividend = d * q
            val ans = q.toLong()
            val stem = "$dividend ÷ $d = ?"
            val c = ArrayList<Long>()
            c.add(ans + 1)
            c.add(ans - 1)
            if (d - 1 >= 2) c.add((dividend / (d - 1)).toLong())
            c.add(((dividend + d) / d).toLong())
            c.add(ans + 10)
            c.add(ans - 10)
            return build(stem, ans, false, 0, pick3(c, ans, false, 0))
        }
    }

    private fun genTwoStep(r: Random): MathProblem? {
        val a = randInt(r, 1, 40); val b = randInt(r, 1, 40); val c = randInt(r, 1, 40)
        var op1 = if (r.nextBoolean()) 1 else -1
        var op2 = if (r.nextBoolean()) 1 else -1
        var ab = a + op1 * b
        if (ab < 1 || ab > 100) { op1 = if (ab < 1) 1 else -1; ab = a + op1 * b }
        var ans = (ab + op2 * c).toLong()
        if (ans < 1 || ans > 100) { op2 = if (ans < 1) 1 else -1; ans = (ab + op2 * c).toLong() }
        val stem = "($a ${if (op1 > 0) "+" else "-"} $b)${if (op2 > 0) "+" else "-"} $c = ?"
        val cands = ArrayList<Long>()
        cands.add(ans + 1)
        cands.add(ans - 1)
        val wrongOrder = (a + op1 * (b + op2 * c)).toLong()
        if (wrongOrder >= 1) cands.add(wrongOrder)
        cands.add(ans + 10)
        cands.add(ans - 10)
        for (i in 0 until 6) cands.add(1L + r.nextInt(min(99, (abs(ans) * 2 + 2).toInt())))
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun genMixedParen(r: Random): MathProblem? {
        val ans: Long
        val stem: String
        val cands = ArrayList<Long>()
        if (r.nextBoolean()) {
            val a = randInt(r, 2, 20); var b = randInt(r, 1, 40); val c = randInt(r, 1, 40)
            if (b + c > 100) b = randInt(r, 1, maxOf(1, 50 - c))
            ans = (a * (b + c)).toLong()
            stem = "$a × ($b + $c) = ?"
            cands.add((a * b + c).toLong())
        } else {
            var a = randInt(r, 1, 40); val b = randInt(r, 1, 40); val c = randInt(r, 2, 20)
            if (a + b > 100) a = randInt(r, 1, maxOf(1, 50 - b))
            ans = ((a + b) * c).toLong()
            stem = "($a + $b) × $c = ?"
            cands.add((a + b * c).toLong())
        }
        cands.add(ans + 1)
        cands.add(ans - 1)
        val pv = placeValueError(ans)
        if (pv > 0) cands.add(pv)
        cands.add(ans + 10)
        cands.add(ans - 10)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun genTwoDigitXOne(r: Random): MathProblem? {
        val ab = randInt(r, 10, 99)
        val c = randInt(r, 2, 9)
        val ans = (ab * c).toLong()
        val stem = "$ab × $c = ?"
        val cands = ArrayList<Long>()
        cands.add((ab + 1) * c.toLong())
        cands.add((ab - 1) * c.toLong())
        cands.add(ans + 1)
        cands.add(ans - 1)
        val pv = ((ab / 10 * 10) * c + ((ab % 10) * c) % 10).toLong()
        if (pv > 0 && pv != ans) cands.add(pv)
        cands.add(ans + 10)
        cands.add(ans - 10)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun genMixedTwoStep(r: Random): MathProblem? {
        var ans: Long
        val stem: String
        val cands = ArrayList<Long>()
        if (r.nextBoolean()) {
            val a = randInt(r, 2, 12); val b = randInt(r, 2, 12); var c = randInt(r, 1, 30)
            ans = (a * b - c).toLong()
            if (ans < 1) { c = randInt(r, 1, (a * b - 1)); ans = (a * b - c).toLong() }
            stem = "$a × $b - $c = ?"
            val wrong = (a * (b - c)).toLong()
            if (wrong >= 1) cands.add(wrong)
        } else {
            val a = randInt(r, 1, 40); var b = randInt(r, 2, 12); val c = randInt(r, 1, 40)
            ans = (a + b * c).toLong()
            if (ans > 200) { b = randInt(r, 2, maxOf(2, (200 - a) / c)); ans = (a + b * c).toLong() }
            stem = "$a + $b × $c = ?"
            cands.add(((a + b) * c).toLong())
        }
        cands.add(ans + 1)
        cands.add(ans - 1)
        cands.add(ans + 10)
        cands.add(ans - 10)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun genDecimalAddSub(r: Random): MathProblem? {
        val digits = 1
        val scale = 10L
        var a = randInt(r, 1, 90)
        var b = randInt(r, 1, 90)
        val sub = r.nextBoolean()
        var ansFp: Long
        val stem: String
        if (sub) {
            if (a < b) { val t = a; a = b; b = t }
            ansFp = (a - b).toLong()
            if (ansFp < 1) { a = b + randInt(r, 1, 90); ansFp = (a - b).toLong() }
            stem = "${fmtDecimal(a.toLong(), digits)} - ${fmtDecimal(b.toLong(), digits)} = ?"
        } else {
            ansFp = (a + b).toLong()
            if (ansFp > 990) { a = randInt(r, 1, maxOf(1, 990 - b)); ansFp = (a + b).toLong() }
            stem = "${fmtDecimal(a.toLong(), digits)} + ${fmtDecimal(b.toLong(), digits)} = ?"
        }
        val cands = ArrayList<Long>()
        cands.add(ansFp * 10)
        cands.add(ansFp + scale)
        cands.add(ansFp - scale)
        cands.add(ansFp + 1)
        cands.add(ansFp - 1)
        if (sub) cands.add((a + b).toLong())
        else if (a > b) cands.add((a - b).toLong())
        return buildDecimal(stem, ansFp, digits, pick3(cands, ansFp, true, digits))
    }

    private fun genMixedThree(r: Random): MathProblem? {
        val a = randInt(r, 1, 30); val b = randInt(r, 2, 12); val c = randInt(r, 2, 12); var d = randInt(r, 1, 30)
        var ans = (a + b * c - d).toLong()
        if (ans < 1) { d = randInt(r, 1, (a + b * c - 1)); ans = (a + b * c - d).toLong() }
        val stem = "$a + $b × $c - $d = ?"
        val cands = ArrayList<Long>()
        val wrong1 = ((a + b) * c - d).toLong()
        val wrong2 = (a + b * (c - d)).toLong()
        if (wrong1 >= 1) cands.add(wrong1)
        if (wrong2 >= 1) cands.add(wrong2)
        cands.add(ans + 1)
        cands.add(ans - 1)
        cands.add(ans + 10)
        cands.add(ans - 10)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun genFractionAddSub(r: Random): MathProblem? {
        val d1 = randInt(r, 2, 12); val d2 = randInt(r, 2, 12)
        var n1 = randInt(r, 1, d1); var n2 = randInt(r, 1, d2)
        var sub = r.nextBoolean()
        var ans = reduce(if (sub) (n1 * d2 - n2 * d1).toLong() else (n1 * d2 + n2 * d1).toLong(), (d1 * d2).toLong())
        if (ans == null || ans[0] < 1) {
            n1 = randInt(r, 1, d1); n2 = randInt(r, 1, d2)
            sub = false
            ans = reduce((n1 * d2 + n2 * d1).toLong(), (d1 * d2).toLong())
        }
        val stem = "$n1/$d1${if (sub) " - " else " + "}$n2/$d2 = ?"
        val cands = ArrayList<LongArray>()
        cands.add(reduce((n1 + n2).toLong(), (d1 + d2).toLong()) ?: longArrayOf())
        cands.add(reduce((n1 + 1).toLong(), d1.toLong()) ?: longArrayOf())
        cands.add(reduce(maxOf(1, n1 - 1).toLong(), d1.toLong()) ?: longArrayOf())
        cands.add(reduce(n1.toLong(), (d1 + 1).toLong()) ?: longArrayOf())
        cands.add(reduce(n1.toLong(), maxOf(2, d1 - 1).toLong()) ?: longArrayOf())
        if (sub) cands.add(reduce((n1 * d2 + n2 * d1).toLong(), (d1 * d2).toLong()) ?: longArrayOf())
        for (i in 2..12) {
            cands.add(reduce(n1.toLong(), i.toLong()) ?: longArrayOf())
            cands.add(reduce(i.toLong(), d1.toLong()) ?: longArrayOf())
        }
        return buildFrac(stem, ans!!, pick3Frac(cands, ans!!))
    }

    private fun genDecimalMulDiv(r: Random): MathProblem? {
        val scale = 10L
        if (r.nextBoolean()) {
            val a = randInt(r, 1, 90)
            val c = randInt(r, 1, 90)
            val ansFp = (a * c).toLong()
            val ansDigits = 2
            val stem = "${fmtDecimal(a.toLong(), 1)} × ${fmtDecimal(c.toLong(), 1)} = ?"
            val cands = ArrayList<Long>()
            cands.add(ansFp * 10)
            cands.add(ansFp + scale * scale)
            cands.add(ansFp - scale * scale)
            cands.add(ansFp + scale)
            cands.add(ansFp - scale)
            return buildDecimal(stem, ansFp, ansDigits, pick3(cands, ansFp, true, ansDigits))
        } else {
            val divisor = randInt(r, 1, 90)
            val rFp = randInt(r, 1, 90)
            val dividend = rFp * divisor
            val ansDigits = 1
            val ansFp = rFp.toLong()
            val stem = "${fmtDecimal(dividend.toLong(), 2)} ÷ ${fmtDecimal(divisor.toLong(), 1)} = ?"
            val cands = ArrayList<Long>()
            cands.add(ansFp * 10)
            cands.add(ansFp + scale)
            cands.add(ansFp - scale)
            cands.add(ansFp + 1)
            cands.add(ansFp - 1)
            return buildDecimal(stem, ansFp, ansDigits, pick3(cands, ansFp, true, ansDigits))
        }
    }

    /** 单位换算：米/厘米、千克/克、分钟/秒、升/毫升，答案都是整数。 */
    private fun genUnitConvert(r: Random): MathProblem? {
        val ans: Long
        val stem: String
        val factor: Long
        when (r.nextInt(4)) {
            0 -> {
                val m = randInt(r, 1, 9)
                ans = (m * 100).toLong(); factor = 100
                stem = "$m 米 = ? 厘米"
            }
            1 -> {
                val kg = randInt(r, 1, 9)
                ans = (kg * 1000).toLong(); factor = 1000
                stem = "$kg 千克 = ? 克"
            }
            2 -> {
                val minutes = randInt(r, 1, 9)
                ans = (minutes * 60).toLong(); factor = 60
                stem = "$minutes 分钟 = ? 秒"
            }
            else -> {
                val liters = randInt(r, 1, 9)
                ans = (liters * 1000).toLong(); factor = 1000
                stem = "$liters 升 = ? 毫升"
            }
        }
        val cands = ArrayList<Long>()
        cands.add(ans * 10)
        if (ans / 10 > 0) cands.add(ans / 10)
        cands.add(ans + factor)
        cands.add(ans - factor)
        cands.add(ans + 1)
        cands.add(ans - 1)
        cands.add(ans + 10)
        cands.add(ans - 10)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    /** 找规律：等差或等比数列，问下一个数。 */
    private fun genNumberPattern(r: Random): MathProblem? {
        val ans: Long
        val stem: String
        val cands = ArrayList<Long>()
        if (r.nextBoolean()) {
            val start = randInt(r, 1, 12)
            val step = randInt(r, 2, 9)
            val terms = LongArray(4) { (start + step * it).toLong() }
            ans = start + step * 4L
            stem = terms.joinToString(", ") + ", ?"
            cands.add(ans + step)
            cands.add(ans - step)
            cands.add(ans + 1)
            cands.add(ans - 1)
            cands.add(ans + 10)
            cands.add(ans - 10)
        } else {
            val start = randInt(r, 1, 5)
            val terms = LongArray(4) { (start * (1 shl it)).toLong() }
            ans = (start * 16L)
            stem = terms.joinToString(", ") + ", ?"
            cands.add(ans / 2)
            cands.add(ans + 2)
            cands.add(ans - 2)
            cands.add(ans + 4)
            cands.add(ans - 4)
        }
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    /** 应用题：单价/剩余/平均分/行程/周长/装箱，全部整数答案。 */
    private fun genWordProblem(r: Random): MathProblem? {
        val ans: Long
        val stem: String
        when (r.nextInt(6)) {
            0 -> {
                val price = randInt(r, 2, 12)
                val count = randInt(r, 2, 9)
                ans = (price * count).toLong()
                stem = "每本笔记本 $price 元，买 $count 本一共多少元？"
            }
            1 -> {
                val total = randInt(r, 10, 50)
                val eaten = randInt(r, 1, total - 1)
                ans = (total - eaten).toLong()
                stem = "有 $total 颗糖，吃掉了 $eaten 颗，还剩多少颗？"
            }
            2 -> {
                val kids = randInt(r, 2, 6)
                val per = randInt(r, 2, 9)
                ans = per.toLong()
                stem = "把 ${kids * per} 个苹果平均分给 $kids 人，每人分到多少个？"
            }
            3 -> {
                val speed = randInt(r, 2, 6)
                val hours = randInt(r, 2, 5)
                ans = (speed * hours).toLong()
                stem = "小明每小时走 $speed 千米，走了 $hours 小时，一共走了多少千米？"
            }
            4 -> {
                val length = randInt(r, 3, 12)
                val width = randInt(r, 3, 12)
                ans = (2 * (length + width)).toLong()
                stem = "一个长方形长 $length 厘米、宽 $width 厘米，周长是多少厘米？"
            }
            else -> {
                val perBox = randInt(r, 6, 12)
                val boxes = randInt(r, 2, 9)
                ans = (perBox * boxes).toLong()
                stem = "一箱牛奶有 $perBox 盒，$boxes 箱一共多少盒？"
            }
        }
        val cands = ArrayList<Long>()
        cands.add(ans + 1)
        cands.add(ans - 1)
        cands.add(ans + 2)
        cands.add(ans - 2)
        cands.add(ans + 10)
        cands.add(ans - 10)
        cands.add(ans + 100)
        cands.add(ans - 100)
        return build(stem, ans, false, 0, pick3(cands, ans, false, 0))
    }

    private fun randInt(r: Random, lo: Int, hi: Int): Int {
        var lo = lo; var hi = hi
        if (hi < lo) { val t = lo; lo = hi; hi = t }
        return lo + r.nextInt(hi - lo + 1)
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        for (i in 0 until n) r *= 10
        return r
    }

    private fun digitCount(n: Long): Int {
        if (n == 0L) return 1
        var c = 0
        var x = abs(n)
        while (x > 0) { c++; x /= 10 }
        return c
    }

    private fun fmtDecimal(fp: Long, digits: Int): String {
        val sb = StringBuilder()
        var fp = fp
        if (fp < 0) { sb.append('-'); fp = -fp }
        val scale = pow10(digits)
        val ip = fp / scale
        val frac = fp % scale
        sb.append(ip)
        if (digits > 0) {
            sb.append('.')
            var f = frac.toString()
            while (f.length < digits) f = "0$f"
            sb.append(f)
        }
        return sb.toString()
    }

    private fun magDigit(fp: Long, isDecimal: Boolean, digits: Int): Int {
        if (!isDecimal) return digitCount(abs(fp))
        val scale = pow10(digits)
        return digitCount(abs(fp) / scale)
    }

    private fun placeValueError(ans: Long): Long {
        val dc = digitCount(ans)
        if (dc < 2) return -1
        val p = pow10(dc - 1)
        return ans - p
    }

    private fun reduce(num: Long, den: Long): LongArray? {
        if (den <= 0) return null
        if (num <= 0) return longArrayOf(num, den)
        val g = gcd(abs(num), den)
        return longArrayOf(num / g, den / g)
    }

    private fun gcd(a: Long, b: Long): Long {
        var a = a; var b = b
        while (b != 0L) { val t = b; b = a % b; a = t }
        return a
    }

    private fun fracEqual(x: LongArray, y: LongArray): Boolean {
        return x[0] * y[1] == y[0] * x[1]
    }

    private fun select3(cands: List<Long>, answerVal: Long, isDecimal: Boolean, digits: Int): List<String> {
        val used = HashSet<String>()
        val out = ArrayList<String>()
        val dc = magDigit(answerVal, isDecimal, digits)
        for (c in cands) {
            if (out.size >= 3) break
            if (c <= 0) continue
            if (c == answerVal) continue
            val cdc = magDigit(c, isDecimal, digits)
            if (abs(cdc - dc) > 1) continue
            val t = if (isDecimal) fmtDecimal(c, digits) else c.toString()
            if (used.add(t)) out.add(t)
        }
        return out
    }

    private fun pick3(cands: List<Long>, answerVal: Long, isDecimal: Boolean, digits: Int): List<String> {
        val out = select3(cands, answerVal, isDecimal, digits)
        if (out.size >= 3) return out
        val extra = ArrayList(cands)
        for (d in 1..9) { extra.add(answerVal + d); extra.add(answerVal - d) }
        for (d in 10..90 step 10) { extra.add(answerVal + d); extra.add(answerVal - d) }
        return select3(extra, answerVal, isDecimal, digits)
    }

    private fun select3Frac(cands: List<LongArray>, answer: LongArray): List<String> {
        val used = HashSet<String>()
        val out = ArrayList<String>()
        val ndc = digitCount(answer[0])
        val ddc = digitCount(answer[1])
        for (c in cands) {
            if (out.size >= 3) break
            if (c[0] <= 0 || c[1] <= 0) continue
            if (fracEqual(c, answer)) continue
            if (abs(digitCount(c[0]) - ndc) > 1) continue
            if (abs(digitCount(c[1]) - ddc) > 1) continue
            val t = "${c[0]}/${c[1]}"
            if (used.add(t)) out.add(t)
        }
        return out
    }

    private fun pick3Frac(cands: List<LongArray>, answer: LongArray): List<String> {
        val out = select3Frac(cands, answer)
        if (out.size >= 3) return out
        val extra = ArrayList(cands)
        for (i in 1..9) {
            extra.add(reduce(answer[0] + i, answer[1]) ?: longArrayOf())
            extra.add(reduce(maxOf(1, answer[0] - i), answer[1]) ?: longArrayOf())
            extra.add(reduce(answer[0], answer[1] + i) ?: longArrayOf())
        }
        return select3Frac(extra, answer)
    }

    private fun build(stem: String, answerVal: Long, isDecimal: Boolean, digits: Int, distractors: List<String>?): MathProblem? {
        if (distractors == null || distractors.size != 3) return null
        val answerText = if (isDecimal) fmtDecimal(answerVal, digits) else answerVal.toString()
        val id = "math:${stem.replace("?", "").trim()}$answerText"
        return MathProblem(stem, answerText, distractors, if (isDecimal) Format.DECIMAL else Format.INT, digits, id)
    }

    private fun buildDecimal(stem: String, answerFp: Long, digits: Int, distractors: List<String>?): MathProblem? {
        if (distractors == null || distractors.size != 3) return null
        val answerText = fmtDecimal(answerFp, digits)
        val id = "math:${stem.replace("?", "").trim()}$answerText"
        return MathProblem(stem, answerText, distractors, Format.DECIMAL, digits, id)
    }

    private fun buildFrac(stem: String, answer: LongArray, distractors: List<String>?): MathProblem? {
        if (distractors == null || distractors.size != 3) return null
        val answerText = "${answer[0]}/${answer[1]}"
        val id = "math:${stem.replace("?", "").trim()}$answerText"
        return MathProblem(stem, answerText, distractors, Format.FRACTION, 0, id)
    }
}
