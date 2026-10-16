package com.kidslock.app.question

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class AssetBankSource(context: Context) : BankSource {
    companion object {
        private const val TAG = "KidsLock-Question"
    }

    private val appContext = context.applicationContext

    override fun load(subject: String, grade: Int): BankRepository.LoadResult? {
        val l1 = tryLoadGrade(subject, grade)
        if (l1 != null && l1.usable()) return l1

        for (cg in adjacentGrades(grade)) {
            if (cg == grade) continue
            val r = tryLoadGrade(subject, cg)
            if (r != null && r.usable()) {
                Log.w(TAG, "题库L2回退: subject=$subject 原年级=$grade 改用年级=$cg")
                return BankRepository.LoadResult(r.entries, 2, intArrayOf(cg))
            }
        }
        return null
    }

    override fun priority() = 1

    private fun tryLoadGrade(subject: String, grade: Int): BankRepository.LoadResult? {
        val path = BankRepository.assetPath(subject, grade)
        return try {
            appContext.assets.open(path).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                val sb = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    sb.append(line)
                    line = reader.readLine()
                }
                val obj = JSONObject(sb.toString())
                val arr = obj.optJSONArray("entries") ?: return BankRepository.LoadResult(emptyList(), 1, intArrayOf(grade))
                val list = ArrayList<BankEntry>()
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val k = e.optString("k", "")
                    val v = e.optString("v", "")
                    if (k.isEmpty() || v.isEmpty()) continue
                    list.add(BankEntry(k, v))
                }
                BankRepository.LoadResult(list, 1, intArrayOf(grade))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun adjacentGrades(grade: Int): List<Int> {
        val out = ArrayList<Int>()
        for (d in 1..Grade.MAX) {
            val lo = grade - d
            val hi = grade + d
            if (lo in Grade.MIN..Grade.MAX) out.add(lo)
            if (hi in Grade.MIN..Grade.MAX) out.add(hi)
        }
        return out
    }
}
