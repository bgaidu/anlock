package com.kidslock.app.question

import android.content.Context
import android.util.Log
import com.kidslock.app.Diag
import com.kidslock.app.PrefManager
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Random

class QuestionEngine private constructor(
    private val context: Context,
    private val providers: List<QuestionProvider>,
    private val providerBySubject: Map<String, QuestionProvider>,
    private val schedule: List<String>,
    private val grade: Int,
    private val gradeBySubject: Map<String, Int>,
    private val judgmentEnabled: Boolean,
    private val reviewBoostEnabled: Boolean,
    private val reviewBoostRatio: Int,
    private val fillBlankEnabled: Boolean
) {
    companion object {
        private const val TAG = "KidsLock-Question"
        private const val MAX_ATTEMPTS = 20

        @JvmStatic
        fun create(ctx: Context, pref: PrefManager): QuestionEngine {
            val baseGrade = pref.grade
            val adaptiveOn = pref.isAdaptiveDifficultyEnabled()
            val before = if (adaptiveOn) pref.getAdaptiveGrade(baseGrade) else baseGrade
            var grade = AdaptiveDifficulty.next(
                current = before,
                base = baseGrade,
                recentAccuracy = pref.getRecentAccuracyPercent(),
                sampleCount = pref.getRecentAnsweredCount(),
                enabled = adaptiveOn
            )
            if (adaptiveOn && grade != before) {
                // 调整过难度后清空最近答题，避免下一次解锁立刻再跳一档
                pref.setAdaptiveGrade(grade)
                pref.consumeRecentAnswers()
                Log.i(TAG, "自适应难度: $before -> $grade (基准 $baseGrade)")
                Diag.i("Question", "adaptive grade $before -> $grade (base=$baseGrade)")
            } else if (!adaptiveOn && pref.getRecentAnsweredCount() > 0) {
                // 关闭自适应后回到家长设定年级
                pref.clearAdaptiveGrade()
                grade = baseGrade
            }
            val subjects = pref.subjects
            val total = pref.unlockCount

            val list = ArrayList<QuestionProvider>()
            val bySubject = HashMap<String, QuestionProvider>()
            val gradesBySubject = HashMap<String, Int>()
            val effective = LinkedHashSet<String>()
            for (s in Subject.ORDER) {
                if (!subjects.contains(s)) continue
                val p = providerFor(ctx, s) ?: continue
                // 单独设置过年级的科目用自己的年级；其余科目跟随总年级/自适应结果
                val subjectGrade = if (pref.hasSubjectGrade(s) || !adaptiveOn) {
                    pref.gradeFor(s)
                } else {
                    grade
                }
                if (!p.isAvailable(subjectGrade)) {
                    Log.w(TAG, "QuestionEngine: 科目不可用，跳过 $s（grade=$subjectGrade）")
                    continue
                }
                list.add(p)
                bySubject[s] = p
                gradesBySubject[s] = subjectGrade
                effective.add(s)
            }

            if (list.isEmpty()) {
                Log.e(TAG, "QuestionEngine: 无可用科目，强制回退 PinyinProvider（R03 L3）")
                val p = PinyinProvider(ctx)
                list.add(p)
                bySubject[Subject.PINYIN] = p
                gradesBySubject[Subject.PINYIN] = grade
                effective.add(Subject.PINYIN)
            }

            val schedule = buildSchedule(effective, total, Random())
            return QuestionEngine(
                ctx, list, bySubject, schedule, grade, gradesBySubject,
                pref.isJudgmentEnabled(), pref.isReviewBoostEnabled(),
                pref.getReviewBoostRatio(), pref.isFillBlankEnabled()
            )
        }

        @JvmStatic
        private fun providerFor(ctx: Context, s: String): QuestionProvider? {
            return when (s) {
                Subject.PINYIN -> PinyinProvider(ctx)
                Subject.MATH -> MathProvider(ctx)
                Subject.ENGLISH -> EnglishProvider(ctx)
                Subject.CITU -> CituProvider(ctx)
                else -> null
            }
        }

        @JvmStatic
        fun unavailableSubjects(ctx: Context, pref: PrefManager): List<String> {
            val subjects = pref.subjects
            val blocked = ArrayList<String>()
            for (s in Subject.ORDER) {
                if (!subjects.contains(s)) continue
                val p = providerFor(ctx, s) ?: continue
                if (!p.isAvailable(pref.gradeFor(s))) blocked.add(s)
            }
            return blocked
        }

        @JvmStatic
        fun buildSchedule(subjects: Set<String>, total: Int, rnd: Random): List<String> {
            var total = total
            if (total < 1) total = 1
            val list = ArrayList(subjects)
            val counts = HashMap<String, Int>()
            for (s in list) counts[s] = 1

            var remaining = total - list.size
            if (remaining < 0) {
                for (k in total until list.size) counts[list[k]] = 0
                remaining = 0
            }

            val m = list.size
            val applyCaps = total > 5
            val cap = if (applyCaps) Math.ceil(total.toDouble() / m).toInt() else Int.MAX_VALUE
            val pinOn = subjects.contains(Subject.PINYIN)
            val ciyuOn = subjects.contains(Subject.CITU)

            var guard = 0
            while (remaining > 0 && guard < 10000) {
                guard++
                var added = false
                for (s in list) {
                    if (remaining <= 0) break
                    if (!applyCaps || canAdd(s, counts, cap, pinOn, ciyuOn)) {
                        counts[s] = counts[s]!! + 1
                        remaining--
                        added = true
                    }
                }
                if (!added) break
            }

            val schedule = ArrayList<String>()
            for (s in list) {
                val c = counts[s]!!
                for (k in 0 until c) schedule.add(s)
            }
            Collections.shuffle(schedule, rnd)
            return schedule
        }

        private fun canAdd(
            s: String,
            counts: Map<String, Int>,
            cap: Int,
            pinOn: Boolean,
            ciyuOn: Boolean
        ): Boolean {
            if (s == Subject.MATH) return counts[s]!! < cap
            if (s == Subject.ENGLISH) return counts[s]!! < cap
            if (s == Subject.PINYIN) {
                return if (ciyuOn) (counts[Subject.PINYIN]!! + counts[Subject.CITU]!!) < cap
                else counts[s]!! < cap
            }
            if (s == Subject.CITU) {
                return if (pinOn) (counts[Subject.PINYIN]!! + counts[Subject.CITU]!!) < cap
                else counts[s]!! < cap
            }
            return true
        }
    }

    private val recent = RecentQuestionQueue()
    private val random = Random()
    private var schedulePos = 0
    private val reviewQueue = ArrayDeque<Question>()
    private var sinceJudgment = 0

    init {
        // 到期错题优先复习：占比由家长设置（默认最多 1/3，至少 1 道）
        if (reviewBoostEnabled) {
            val limit = ReviewQuota.limit(schedule.size, reviewBoostRatio)
            reviewQueue.addAll(ReviewStore.dueQuestions(context, limit))
        }
    }

    /** 把答题结果写进错题池 / SM-2 复习计划。 */
    fun recordResult(q: Question, correct: Boolean) {
        ReviewStore.recordResult(context, q, correct)
    }

    private fun gradeFor(subject: String?): Int =
        if (subject == null) grade else (gradeBySubject[subject] ?: grade)

    fun nextQuestion(): Question {
        if (reviewQueue.isNotEmpty()) {
            return reviewQueue.removeFirst()
        }
        val q = nextQuestion(false)
        if (q != null) return q
        val relaxed = nextQuestion(true) ?: throw IllegalStateException("nextQuestion returned null")
        return relaxed
    }

    private fun nextQuestion(relax: Boolean): Question? {
        // 判断题：每 3 道左右穿插 1 道，避免题型过于单一
        if (judgmentEnabled && sinceJudgment >= 3) {
            val judgment = nextJudgmentQuestion()
            if (judgment != null) {
                sinceJudgment = 0
                recent.offer(judgment.id)
                return judgment
            }
        }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val p = providerForScheduledSubject()
            val provider = p ?: providers[random.nextInt(providers.size)]
            val subjectGrade = gradeFor(provider.subject())
            // 填空题：数学题改成自己输入答案，其余科目保持选择题
            val q = if (fillBlankEnabled && provider is MathProvider) {
                provider.nextFill(subjectGrade, random)
            } else {
                provider.next(subjectGrade, random)
            } ?: continue
            if (relax || !recent.contains(q.id)) {
                recent.offer(q.id)
                sinceJudgment++
                return q
            }
        }
        return null
    }

    /**
     * 用题库生成判断题：随机取一条，50% 展示正确读音/释义，50% 换成同题库里的另一条。
     * 复用现有的「多选项」渲染，选项固定为「对 / 错」。
     */
    private fun nextJudgmentQuestion(): Question? {
        val subjects = providers.map { it.subject() }.filter { it != Subject.MATH }
        if (subjects.isEmpty()) return null
        val subject = subjects[random.nextInt(subjects.size)]
        val result = BankRepository.get(context).load(subject, gradeFor(subject))
        if (result.entries.size < 4) return null
        val entry = result.entries[random.nextInt(result.entries.size)]

        val truthful = random.nextBoolean()
        var shown = entry.v
        if (!truthful) {
            for (attempt in 0 until 12) {
                val other = result.entries[random.nextInt(result.entries.size)]
                if (other.v != entry.v) {
                    shown = other.v
                    break
                }
            }
            // 题库里去重值不足时退回正确说法，避免出现"唯一答案也被判错"
            if (shown == entry.v) return nextJudgmentFromProvider()
        }

        val hint = when (subject) {
            Subject.PINYIN -> "判断：「${entry.k}」的读音是 ${shown} 吗？"
            Subject.ENGLISH -> "判断：「${entry.k}」的意思是 ${shown} 吗？"
            else -> "判断：「${entry.k}」的拼音是 ${shown} 吗？"
        }
        // 纯逻辑层不依赖 Android 字体：用中文字符，任何设备都能画出来
        val options = listOf("对", "错")
        return Question(
            stem = entry.k,
            hint = hint,
            options = options,
            correctIndex = if (truthful) 0 else 1,
            subject = subject,
            id = "judge:$subject:${entry.k}:$shown",
            stemStyle = Question.StemStyle.NORMAL
        )
    }

    private fun nextJudgmentFromProvider(): Question? {
        val p = providers[random.nextInt(providers.size)]
        return p.next(gradeFor(p.subject()), random)
    }

    fun nextPreviewQuestion(): Question {
        for (i in providers.indices) {
            val p = providerForScheduledSubject() ?: providers[random.nextInt(providers.size)]
            val q = p.next(gradeFor(p.subject()), random)
            if (q != null) return q
        }
        for (p in providers) {
            val q = p.next(gradeFor(p.subject()), random)
            if (q != null) return q
        }
        return nextQuestion()
    }

    private fun providerForScheduledSubject(): QuestionProvider? {
        if (schedule.isEmpty()) return null
        val subj = schedule[schedulePos]
        schedulePos++
        if (schedulePos >= schedule.size) {
            schedulePos = 0
            Collections.shuffle(schedule, random)
        }
        return providerBySubject[subj]
    }

    fun activeSubjects(): List<String> {
        return providers.map { it.subject() }
    }
}
