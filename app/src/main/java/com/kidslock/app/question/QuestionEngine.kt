package com.kidslock.app.question

import android.content.Context
import android.util.Log
import com.kidslock.app.PrefManager
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Random

class QuestionEngine private constructor(
    private val context: Context,
    private val providers: List<QuestionProvider>,
    private val providerBySubject: Map<String, QuestionProvider>,
    private val schedule: List<String>,
    private val grade: Int
) {
    companion object {
        private const val TAG = "KidsLock-Question"
        private const val MAX_ATTEMPTS = 20

        @JvmStatic
        fun create(ctx: Context, pref: PrefManager): QuestionEngine {
            val grade = pref.grade
            val subjects = pref.subjects
            val total = pref.unlockCount

            val list = ArrayList<QuestionProvider>()
            val bySubject = HashMap<String, QuestionProvider>()
            val effective = LinkedHashSet<String>()
            for (s in Subject.ORDER) {
                if (!subjects.contains(s)) continue
                val p = providerFor(ctx, s) ?: continue
                if (!p.isAvailable(grade)) {
                    Log.w(TAG, "QuestionEngine: 科目不可用，跳过 $s（grade=$grade）")
                    continue
                }
                list.add(p)
                bySubject[s] = p
                effective.add(s)
            }

            if (list.isEmpty()) {
                Log.e(TAG, "QuestionEngine: 无可用科目，强制回退 PinyinProvider（R03 L3）")
                val p = PinyinProvider(ctx)
                list.add(p)
                bySubject[Subject.PINYIN] = p
                effective.add(Subject.PINYIN)
            }

            val schedule = buildSchedule(effective, total, Random())
            return QuestionEngine(ctx, list, bySubject, schedule, grade)
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
            val grade = pref.grade
            val subjects = pref.subjects
            val blocked = ArrayList<String>()
            for (s in Subject.ORDER) {
                if (!subjects.contains(s)) continue
                val p = providerFor(ctx, s) ?: continue
                if (!p.isAvailable(grade)) blocked.add(s)
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

    fun nextQuestion(): Question {
        val q = nextQuestion(false)
        if (q != null) return q
        val relaxed = nextQuestion(true) ?: throw IllegalStateException("nextQuestion returned null")
        return relaxed
    }

    private fun nextQuestion(relax: Boolean): Question? {
        for (attempt in 0 until MAX_ATTEMPTS) {
            val p = providerForScheduledSubject()
            val q = (p ?: providers[random.nextInt(providers.size)]).next(grade, random) ?: continue
            if (relax || !recent.contains(q.id)) {
                recent.offer(q.id)
                return q
            }
        }
        return null
    }

    fun nextPreviewQuestion(): Question {
        for (i in providers.indices) {
            val p = providerForScheduledSubject()
            val q = (p ?: providers[random.nextInt(providers.size)]).next(grade, random)
            if (q != null) return q
        }
        for (p in providers) {
            val q = p.next(grade, random)
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
