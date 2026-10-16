package com.kidslock.app.question

import androidx.annotation.Nullable
import java.util.Collections
import java.util.HashSet
import java.util.Random

object DistractorSampler {
    @JvmStatic
    @Nullable
    fun sample(
        pool: List<BankEntry>,
        chosen: BankEntry,
        answerKey: Boolean,
        random: Random
    ): List<String>? {
        val chosenVal = if (answerKey) chosen.k else chosen.v

        val candidates = HashSet<String>()
        for (e in pool) {
            val v = if (answerKey) e.k else e.v
            if (v != chosenVal) candidates.add(v)
        }
        if (candidates.size < 3) return null

        val cands = ArrayList(candidates)
        Collections.shuffle(cands, random)

        val options = ArrayList<String>(4)
        options.add(chosenVal)
        options.addAll(cands.subList(0, 3))
        Collections.shuffle(options, random)

        if (!isOptionSetValid(options, chosenVal)) return null
        return options
    }

    private fun isOptionSetValid(options: List<String>, answer: String): Boolean {
        if (options.size != 4) return false
        val seen = HashSet<String>()
        var hasAnswer = false
        for (o in options) {
            if (!seen.add(o)) return false
            if (o == answer) hasAnswer = true
        }
        return hasAnswer
    }
}
