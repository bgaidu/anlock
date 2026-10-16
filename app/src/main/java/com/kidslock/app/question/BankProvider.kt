package com.kidslock.app.question

import android.content.Context
import java.util.Random

abstract class BankProvider(
    context: Context,
    final val subject: String,
    final val hint: String,
    final val stemStyle: Question.StemStyle
) : QuestionProvider {

    protected val context: Context = context.getApplicationContext()

    override fun subject(): String = subject

    override fun isAvailable(grade: Int): Boolean {
        return BankRepository.get(context).load(subject, grade).usable()
    }

    override fun next(grade: Int, random: Random): Question? {
        val lr = BankRepository.get(context).load(subject, grade)
        if (!lr.usable()) return null

        val pool = lr.entries
        if (pool.size < 4) return null

        val chosen = pool[random.nextInt(pool.size)]

        val options = DistractorSampler.sample(pool, chosen, false, random) ?: return null
        return buildQuestion(chosen, options, random)
    }

    protected fun buildQuestion(entry: BankEntry, options: List<String>, random: Random): Question {
        val correctIndex = options.indexOf(entry.v)
        val id = "$subject:${entry.k}"
        return Question(entry.k, hint, options, correctIndex, subject, id, stemStyle)
    }
}
