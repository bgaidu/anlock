package com.kidslock.app.question

import android.content.Context
import com.kidslock.app.question.MathSpec.MathKind
import com.kidslock.app.question.MathSpec.MathProblem
import java.util.Collections
import java.util.Random

class MathProvider(private val context: Context?) : QuestionProvider {

    override fun subject(): String = Subject.MATH

    override fun isAvailable(grade: Int): Boolean = Grade.isValid(grade)

    override fun next(grade: Int, random: Random): Question? {
        if (!Grade.isValid(grade)) return null
        val kinds = MathSpec.BY_GRADE[grade] ?: return null
        val kind = kinds[random.nextInt(kinds.size)]
        val problem = MathSpec.generate(kind, random) ?: return null
        if (problem.distractorTexts.size != 3) return null

        val options = ArrayList<String>(4)
        options.add(problem.answerText)
        options.addAll(problem.distractorTexts)

        val seen = HashSet<String>()
        for (o in options) {
            if (!seen.add(o)) return null
        }

        Collections.shuffle(options, random)
        val correctIndex = options.indexOf(problem.answerText)
        if (correctIndex < 0) return null

        return Question(problem.stem, "结果是多少？", options, correctIndex,
                Subject.MATH, problem.id, Question.StemStyle.NORMAL)
    }
}
