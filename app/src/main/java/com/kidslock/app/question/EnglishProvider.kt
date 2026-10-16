package com.kidslock.app.question

import android.content.Context
import java.util.Random

class EnglishProvider(context: Context) : BankProvider(
    context, Subject.ENGLISH, "这个单词是什么意思？", Question.StemStyle.NORMAL
) {

    companion object {
        private const val HINT_FORWARD = "这个单词是什么意思？"
        private const val HINT_REVERSE = "这个意思对应的单词是？"
    }

    override fun next(grade: Int, random: Random): Question? {
        val lr = BankRepository.get(context).load(subject, grade)
        if (!lr.usable()) return null

        val pool = lr.entries
        if (pool.size < 4) return null

        val forward = random.nextBoolean()
        val chosen = pool[random.nextInt(pool.size)]

        val stem: String
        val answer: String
        val hint: String
        val options: List<String>?
        if (forward) {
            stem = chosen.k
            answer = chosen.v
            hint = HINT_FORWARD
            options = DistractorSampler.sample(pool, chosen, false, random)
        } else {
            stem = chosen.v
            answer = chosen.k
            hint = HINT_REVERSE
            options = DistractorSampler.sample(pool, chosen, true, random)
        }
        if (options == null) return null

        val correctIndex = options.indexOf(answer)
        if (correctIndex < 0) return null

        val dirTag = if (forward) "fwd" else "rev"
        val id = "$subject:$dirTag:${chosen.k}"
        return Question(stem, hint, options, correctIndex, subject, id, stemStyle)
    }
}
