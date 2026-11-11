package com.kidslock.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 全量设置备份 / 恢复（换机、重置、误清数据后用）。
 *
 * 设计取舍：
 * - 只备份「家长设置 + 学习统计」，不备份锁屏状态、计时、当日用量、临时放行、局域网口令等临时数据；
 * - **不备份 PIN**：4 位 PIN 的哈希离线爆破成本太低，备份文件可能被转发/网盘留存。
 *   恢复后 PIN 回到默认 1234，家长恢复完立刻改一次即可；
 * - 错题池一起带走，换机后复习计划不中断。
 */
object SettingsBackup {
    private const val APP_ID = "kidslock"
    private const val VERSION = 1
    private const val REVIEW_FILE = "review_pool.json"

    /** 允许导入导出的键。新增设置项时记得加到这里，否则不会被备份。 */
    private val ALLOWED_KEYS: Set<String> = HashSet<String>().apply {
        add("auto_start")
        add("watch_limit_min")
        add("unlock_count")
        add("question_grade")
        add("question_subjects")
        add("question_judgment")
        add("question_fill")
        add("review_boost")
        add("adaptive_difficulty")
        add("adaptive_grade")
        add("stars")
        add("stat_correct")
        add("stat_total")
        add("stat_best_combo")
        add("recent_answers")
        add("star_exchange_stars")
        add("star_exchange_minutes")
        add("daily_limit_min")
        add("daily_limit_weekend_min")
        add("show_countdown")
        add("emergency_reboot_unlock")
        add("weekly_report_enabled")
        add("schedule_enabled")
        add("schedule_free_mode")
        for (day in 0..6) {
            add("schedule_start_$day")
            add("schedule_end_$day")
        }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PrefManager.PREF_NAME, Context.MODE_PRIVATE)

    fun exportJson(ctx: Context): String {
        val p = prefs(ctx)
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val values = JSONObject()
        for (key in ALLOWED_KEYS) {
            if (!p.contains(key)) continue
            val v = p.all[key] ?: continue
            values.put(key, encode(v))
        }
        root.put("settings", values)
        val review = File(ctx.filesDir, REVIEW_FILE)
        if (review.exists()) {
            root.put("reviewPool", review.readText(StandardCharsets.UTF_8))
        }
        return root.toString(2)
    }

    /** @return 成功导入的设置项数量；-1 表示文件格式不认识。 */
    fun importJson(ctx: Context, raw: String): Int {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return -1
        }
        if (root.optString("app") != APP_ID) return -1
        if (root.optInt("version", -1) != VERSION) return -1
        val values = root.optJSONObject("settings") ?: return -1

        val p = prefs(ctx)
        val editor = p.edit()
        var applied = 0
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in ALLOWED_KEYS) continue
            val obj = values.optJSONObject(key) ?: continue
            if (applyTo(editor, key, obj)) applied++
        }
        editor.apply()

        val review = root.optString("reviewPool", "")
        if (review.isNotEmpty()) {
            try {
                File(ctx.filesDir, REVIEW_FILE).writeText(review, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                Diag.w("Backup", "restore review pool failed: ${e.message}")
            }
        }
        Diag.i("Backup", "settings imported: $applied keys")
        return applied
    }

    private fun encode(value: Any): JSONObject = when (value) {
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is String -> JSONObject().put("t", "s").put("v", value)
        is Set<*> -> {
            val arr = org.json.JSONArray()
            value.forEach { arr.put(it.toString()) }
            JSONObject().put("t", "ss").put("v", arr)
        }
        else -> JSONObject().put("t", "s").put("v", value.toString())
    }

    private fun applyTo(editor: SharedPreferences.Editor, key: String, obj: JSONObject): Boolean {
        return try {
            when (obj.optString("t")) {
                "b" -> editor.putBoolean(key, obj.optBoolean("v"))
                "i" -> editor.putInt(key, obj.optInt("v"))
                "l" -> editor.putLong(key, obj.optLong("v"))
                "f" -> editor.putFloat(key, obj.optDouble("v").toFloat())
                "s" -> editor.putString(key, obj.optString("v"))
                "ss" -> {
                    val arr = obj.optJSONArray("v") ?: return false
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.optString(i))
                    editor.putStringSet(key, set)
                }
                else -> return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
