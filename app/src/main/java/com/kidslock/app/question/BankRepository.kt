package com.kidslock.app.question

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class BankRepository private constructor(
    private val context: Context,
    private val sources: List<BankSource>
) {
    companion object {
        private const val TAG = "KidsLock-Question"
        private const val MIN_ENTRIES = 4

        @Volatile
        private var instance: BankRepository? = null

        @JvmStatic
        fun get(ctx: Context): BankRepository {
            return instance ?: synchronized(BankRepository::class.java) {
                instance ?: BankRepository(
                    ctx.applicationContext,
                    listOf(CustomBankSource(ctx), AssetBankSource(ctx), BuiltinBankSource())
                        .sortedBy { it.priority() }
                ).also { instance = it }
            }
        }

        @JvmStatic
        fun assetPath(subject: String, grade: Int): String = "banks/${subject}_${grade}.json"
    }

    private val cache = ConcurrentHashMap<String, LoadResult>()

    fun load(subject: String, grade: Int): LoadResult {
        val key = "${subject}_$grade"
        cache[key]?.let { return it }
        val result = loadInternal(subject, grade)
        cache[key] = result
        return result
    }

    fun clearCache() {
        cache.clear()
    }

    private fun loadInternal(subject: String, grade: Int): LoadResult {
        for (source in sources) {
            val result = source.load(subject, grade)
            if (result != null && result.usable()) return result
        }
        Log.w(TAG, "题库不可用: subject=$subject 年级=$grade")
        return LoadResult(emptyList(), 0, intArrayOf(grade))
    }

    data class LoadResult(
        val entries: List<BankEntry>,
        val level: Int,
        val gradesUsed: IntArray
    ) {
        fun usable(): Boolean = entries.size >= MIN_ENTRIES
    }
}
