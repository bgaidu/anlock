package com.kidslock.app.prefs

import android.content.SharedPreferences
import com.kidslock.app.prefs.PrefKeys.DEFAULT_STAR_EXCHANGE_MINUTES
import com.kidslock.app.prefs.PrefKeys.DEFAULT_STAR_EXCHANGE_STARS
import com.kidslock.app.prefs.PrefKeys.RECENT_ANSWERS
import com.kidslock.app.prefs.PrefKeys.STARS
import com.kidslock.app.prefs.PrefKeys.STAR_EXCHANGE_MINUTES
import com.kidslock.app.prefs.PrefKeys.STAR_EXCHANGE_STARS
import com.kidslock.app.prefs.PrefKeys.STAT_BEST_COMBO
import com.kidslock.app.prefs.PrefKeys.STAT_CORRECT
import com.kidslock.app.prefs.PrefKeys.STAT_TOTAL

/** 星星、连对、正确率统计与星星兑换比例。 */
class RewardStore(private val prefs: SharedPreferences) : RewardSettings {

    override fun getStarExchangeStars(): Int =
        prefs.getInt(STAR_EXCHANGE_STARS, DEFAULT_STAR_EXCHANGE_STARS).coerceAtLeast(1)

    override fun getStarExchangeMinutes(): Int =
        prefs.getInt(STAR_EXCHANGE_MINUTES, DEFAULT_STAR_EXCHANGE_MINUTES).coerceAtLeast(1)

    override fun setStarExchange(stars: Int, minutes: Int) {
        prefs.edit()
            .putInt(STAR_EXCHANGE_STARS, stars.coerceIn(1, 999))
            .putInt(STAR_EXCHANGE_MINUTES, minutes.coerceIn(1, 180))
            .apply()
    }

    override fun getStars(): Int = prefs.getInt(STARS, 0)

    override fun addStars(count: Int) {
        if (count <= 0) return
        prefs.edit().putInt(STARS, getStars() + count).apply()
    }

    override fun getBestCombo(): Int = prefs.getInt(STAT_BEST_COMBO, 0)

    override fun recordCombo(combo: Int) {
        if (combo > getBestCombo()) {
            prefs.edit().putInt(STAT_BEST_COMBO, combo).apply()
        }
    }

    override fun resetStats() {
        prefs.edit()
            .remove(STAT_TOTAL)
            .remove(STAT_CORRECT)
            .remove(STAT_BEST_COMBO)
            .remove(RECENT_ANSWERS)
            .apply()
    }

    override fun getStatTotal(): Int = prefs.getInt(STAT_TOTAL, 0)

    override fun getStatCorrect(): Int = prefs.getInt(STAT_CORRECT, 0)

    override fun getAccuracyPercent(): Int {
        val total = getStatTotal()
        if (total <= 0) return -1
        return Math.round(100f * getStatCorrect() / total)
    }

    override fun recordAnswer(correct: Boolean) {
        val total = prefs.getInt(STAT_TOTAL, 0) + 1
        val correctCount = prefs.getInt(STAT_CORRECT, 0) + if (correct) 1 else 0
        val history = RecentAnswers.append(
            prefs.getString(RECENT_ANSWERS, ""),
            correct
        )
        prefs.edit()
            .putInt(STAT_TOTAL, total)
            .putInt(STAT_CORRECT, correctCount)
            .putString(RECENT_ANSWERS, history)
            .apply()
    }
}
