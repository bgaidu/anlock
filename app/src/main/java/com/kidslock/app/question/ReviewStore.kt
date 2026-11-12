package com.kidslock.app.question

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Random

/**
 * 错题池 + SM-2 间隔重复。
 *
 * 答错的题会被存一份「快照」（题干/选项/正确答案），到期后优先在锁屏答题里出现；
 * 复习答对则按 SM-2 拉长间隔（1 天 → 6 天 → 上次间隔 × 难度系数），
 * 答错则回到「今天再来」并降低难度系数。
 *
 * 只用天为粒度，够用且不会让协议变复杂。
 */
object ReviewStore {
    private const val FILE_NAME = "review_pool.json"
    private const val MAX_ENTRIES = 200
    private const val DAY_MS = 86_400_000L

    data class Item(
        val key: String,
        val subject: String,
        val id: String,
        val stem: String,
        val hint: String,
        val options: List<String>,
        val correctIndex: Int,
        val style: String,
        var ease: Double,
        var intervalDays: Int,
        var dueDay: Long,
        var reps: Int,
        var lapses: Int
    )

    private val lock = Any()
    private val random = Random()

    fun keyOf(q: Question): String = q.subject + "|" + q.id

    private fun fileFor(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    private fun epochDay(nowMillis: Long): Long = nowMillis / DAY_MS

    fun dueQuestions(ctx: Context, limit: Int): List<Question> {
        if (limit <= 0) return emptyList()
        val today = epochDay(System.currentTimeMillis())
        synchronized(lock) {
            val items = load(ctx)
            return items.asSequence()
                .filter { it.dueDay <= today && it.options.size >= 2 && it.correctIndex in it.options.indices }
                .sortedBy { it.dueDay }
                .take(limit)
                .map { toQuestion(it) }
                .toList()
        }
    }

    fun dueCount(ctx: Context): Int {
        val today = epochDay(System.currentTimeMillis())
        synchronized(lock) {
            return load(ctx).count { it.dueDay <= today }
        }
    }

    /** 家长查看用：全部错题，按到期日从早到晚。 */
    fun all(ctx: Context): List<Item> = synchronized(lock) {
        load(ctx).sortedWith(compareBy({ it.dueDay }, { it.subject }, { it.id }))
    }

    fun remove(ctx: Context, key: String) {
        synchronized(lock) {
            val items = load(ctx)
            items.removeAll { it.key == key }
            save(ctx, items)
        }
    }

    fun statusOf(item: Item): ReviewMastery.Status =
        ReviewMastery.status(item.reps, item.intervalDays, item.dueDay, todayDay())

    /** 家长手动「标记已掌握 / 恢复复习」，不改动其他错题。 */
    fun setMastered(ctx: Context, key: String, mastered: Boolean) {
        synchronized(lock) {
            val items = load(ctx)
            val item = items.firstOrNull { it.key == key } ?: return
            val state = if (mastered) {
                ReviewMastery.masteredState(todayDay())
            } else {
                ReviewMastery.restoreState(todayDay())
            }
            item.reps = state.reps
            item.intervalDays = state.intervalDays
            item.dueDay = state.dueDay
            save(ctx, items)
        }
    }

    private fun todayDay(): Long = epochDay(System.currentTimeMillis())

    fun clear(ctx: Context) {
        synchronized(lock) {
            try {
                fileFor(ctx).delete()
            } catch (e: Exception) {
                // 删除失败下次再试
            }
        }
    }

    fun recordResult(ctx: Context, q: Question, correct: Boolean) {
        synchronized(lock) {
            val items = load(ctx)
            val key = keyOf(q)
            val item = items.firstOrNull { it.key == key }
            if (item == null) {
                if (correct) return
                items.add(
                    Item(
                        key = key,
                        subject = q.subject,
                        id = q.id,
                        stem = q.stem,
                        hint = q.hint,
                        options = q.options,
                        correctIndex = q.correctIndex,
                        style = q.stemStyle.name,
                        ease = 2.5,
                        intervalDays = 0,
                        dueDay = epochDay(System.currentTimeMillis()),
                        reps = 0,
                        lapses = 1
                    )
                )
            } else {
                applySm2(item, correct)
            }
            // 池子太大时优先丢弃最久不用、且已到期的旧题，避免文件无限增长
            while (items.size > MAX_ENTRIES) {
                val victim = items.minByOrNull { it.dueDay } ?: break
                items.remove(victim)
            }
            save(ctx, items)
        }
    }

    private fun applySm2(item: Item, correct: Boolean) {
        val today = epochDay(System.currentTimeMillis())
        val next = Sm2.review(
            Sm2.State(
                ease = item.ease,
                intervalDays = item.intervalDays,
                dueDay = item.dueDay,
                reps = item.reps,
                lapses = item.lapses
            ),
            correct,
            today
        )
        item.ease = next.ease
        item.intervalDays = next.intervalDays
        item.dueDay = next.dueDay
        item.reps = next.reps
        item.lapses = next.lapses
    }

    private fun toQuestion(item: Item): Question {
        // 每次出现都打乱选项，避免孩子记住「答案在第几个」
        val shuffled = OptionShuffle.shuffle(item.options, item.correctIndex, random)
        return Question(
            stem = item.stem,
            hint = item.hint,
            options = shuffled.options,
            correctIndex = shuffled.correctIndex,
            subject = item.subject,
            id = item.id,
            stemStyle = if (item.style == Question.StemStyle.HANZI.name)
                Question.StemStyle.HANZI else Question.StemStyle.NORMAL,
            isReview = true
        )
    }

    private fun load(ctx: Context): MutableList<Item> {
        val f = fileFor(ctx)
        if (!f.exists()) return ArrayList()
        return try {
            val text = f.readText(StandardCharsets.UTF_8)
            val root = JSONObject(text)
            val arr = root.optJSONArray("items") ?: return ArrayList()
            val out = ArrayList<Item>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val optionsArr = o.optJSONArray("options") ?: continue
                val options = ArrayList<String>(optionsArr.length())
                for (j in 0 until optionsArr.length()) {
                    options.add(optionsArr.optString(j, ""))
                }
                if (options.size < 2) continue
                out.add(
                    Item(
                        key = o.optString("key", ""),
                        subject = o.optString("subject", ""),
                        id = o.optString("id", ""),
                        stem = o.optString("stem", ""),
                        hint = o.optString("hint", ""),
                        options = options,
                        correctIndex = o.optInt("correctIndex", 0),
                        style = o.optString("style", Question.StemStyle.NORMAL.name),
                        ease = o.optDouble("ease", 2.5),
                        intervalDays = o.optInt("interval", 0),
                        dueDay = o.optLong("due", 0),
                        reps = o.optInt("reps", 0),
                        lapses = o.optInt("lapses", 0)
                    )
                )
            }
            out
        } catch (e: Exception) {
            ArrayList()
        }
    }

    private fun save(ctx: Context, items: List<Item>) {
        try {
            val arr = JSONArray()
            for (it in items) {
                val o = JSONObject()
                o.put("key", it.key)
                o.put("subject", it.subject)
                o.put("id", it.id)
                o.put("stem", it.stem)
                o.put("hint", it.hint)
                o.put("options", JSONArray(it.options))
                o.put("correctIndex", it.correctIndex)
                o.put("style", it.style)
                o.put("ease", it.ease)
                o.put("interval", it.intervalDays)
                o.put("due", it.dueDay)
                o.put("reps", it.reps)
                o.put("lapses", it.lapses)
                arr.put(o)
            }
            val root = JSONObject()
            root.put("version", 1)
            root.put("items", arr)
            fileFor(ctx).writeText(root.toString(), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // 错题池写失败不影响答题，下次再试
        }
    }
}
