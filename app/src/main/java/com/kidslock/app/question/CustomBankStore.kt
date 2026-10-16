package com.kidslock.app.question

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

object CustomBankStore {
    private const val DIR = "custom_banks"
    private const val MIN_ENTRIES = 4
    private val SUPPORTED = setOf(Subject.PINYIN, Subject.ENGLISH, Subject.CITU)

    private fun fileFor(ctx: Context, subject: String, grade: Int): File {
        val dir = File(ctx.filesDir, DIR)
        return File(dir, "${subject}_${grade}.json")
    }

    @JvmStatic
    fun isSupported(subject: String): Boolean = subject in SUPPORTED

    @JvmStatic
    fun hasCustom(ctx: Context, subject: String, grade: Int): Boolean =
        fileFor(ctx, subject, grade).exists()

    @JvmStatic
    fun loadCustom(ctx: Context, subject: String, grade: Int): BankRepository.LoadResult? {
        val f = fileFor(ctx, subject, grade)
        if (!f.exists()) return null
        return try {
            val text = f.readText(StandardCharsets.UTF_8)
            val list = parseEntries(text) ?: return null
            if (list.size < MIN_ENTRIES) return null
            BankRepository.LoadResult(list, 0, intArrayOf(grade))
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun importBank(ctx: Context, subject: String, grade: Int, json: String): Boolean {
        if (!isSupported(subject) || !Grade.isValid(grade)) return false
        val list = parseEntries(json) ?: return false
        if (list.size < MIN_ENTRIES) return false
        return try {
            val outArr = JSONArray()
            for (e in list) {
                val o = JSONObject()
                o.put("k", e.k)
                o.put("v", e.v)
                outArr.put(o)
            }
            val out = JSONObject()
            out.put("entries", outArr)
            val f = fileFor(ctx, subject, grade)
            f.parentFile?.mkdirs()
            f.writeText(out.toString(), StandardCharsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun listCustom(ctx: Context): List<String> {
        val dir = File(ctx.filesDir, DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.sorted()
            ?: emptyList()
    }

    @JvmStatic
    fun clear(ctx: Context, subject: String, grade: Int): Boolean {
        val f = fileFor(ctx, subject, grade)
        if (!f.exists()) return true
        return f.delete()
    }

    private fun parseEntries(json: String): List<BankEntry>? {
        return try {
            val t = json.trim()
            val arr = if (t.startsWith("[")) {
                JSONArray(t)
            } else {
                val obj = JSONObject(t)
                obj.optJSONArray("entries") ?: return null
            }
            val list = ArrayList<BankEntry>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val k = e.optString("k", "")
                val v = e.optString("v", "")
                if (k.isEmpty() || v.isEmpty()) return null
                list.add(BankEntry(k, v))
            }
            list
        } catch (e: Exception) {
            null
        }
    }
}
