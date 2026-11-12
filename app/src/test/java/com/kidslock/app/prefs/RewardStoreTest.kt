package com.kidslock.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardStoreTest {

    private fun newStore() = RewardStore(FakeSharedPreferences())

    @Test
    fun addStars_accumulates() {
        val store = newStore()
        store.addStars(2)
        store.addStars(3)
        assertEquals(5, store.getStars())
    }

    @Test
    fun removeStars_deductsButNeverBelowZero() {
        val store = newStore()
        store.addStars(2)

        assertEquals(1, store.removeStars(1))
        assertEquals(1, store.getStars())

        // 星星只剩 1 颗时，扣 1 颗刚好到 0
        assertEquals(1, store.removeStars(1))
        assertEquals(0, store.getStars())

        // 已经扣完：不再变化，返回实际扣除 0
        assertEquals(0, store.removeStars(1))
        assertEquals(0, store.getStars())
        assertEquals(0, store.removeStars(5))
        assertEquals(0, store.getStars())
    }

    @Test
    fun removeStars_ignoresNonPositiveCount() {
        val store = newStore()
        store.addStars(3)
        assertEquals(0, store.removeStars(0))
        assertEquals(0, store.removeStars(-2))
        assertEquals(3, store.getStars())
    }
}
