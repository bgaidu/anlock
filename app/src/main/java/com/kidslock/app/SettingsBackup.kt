package com.kidslock.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
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
    private const val REVIEW_FILE = "review_pool.json"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PrefManager.PREF_NAME, Context.MODE_PRIVATE)

    fun exportJson(ctx: Context): String {
        val p = prefs(ctx)
        val root = JSONObject()
        root.put("app", SettingsCodec.APP_ID)
        root.put("version", SettingsCodec.VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val values = JSONObject()
        for (key in p.all.keys.sorted()) {
            if (!SettingsCodec.isAllowed(key)) continue
            val v = p.all[key] ?: continue
            values.put(key, packet(SettingsCodec.encode(v)))
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
        if (root.optString("app") != SettingsCodec.APP_ID) return -1
        if (root.optInt("version", -1) != SettingsCodec.VERSION) return -1
        val values = root.optJSONObject("settings") ?: return -1

        val p = prefs(ctx)
        val editor = p.edit()
        var applied = 0
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!SettingsCodec.isAllowed(key)) continue
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

    private fun packet(encoded: SettingsCodec.Encoded): JSONObject {
        val obj = JSONObject().put("t", encoded.type)
        if (encoded.type == "ss") {
            val arr = JSONArray()
            (encoded.value as List<*>).forEach { arr.put(it) }
            obj.put("v", arr)
        } else {
            obj.put("v", encoded.value)
        }
        return obj
    }

    private fun applyTo(
        editor: SharedPreferences.Editor,
        key: String,
        obj: JSONObject
    ): Boolean {
        val type = obj.optString("t")
        val raw: Any? = if (type == "ss") {
            obj.optJSONArray("v")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            }
        } else {
            obj.opt("v")
        }
        val decoded = SettingsCodec.decode(type, raw) ?: return false
        return try {
            when (decoded) {
                is Boolean -> editor.putBoolean(key, decoded)
                is Int -> editor.putInt(key, decoded)
                is Long -> editor.putLong(key, decoded)
                is Float -> editor.putFloat(key, decoded)
                is String -> editor.putString(key, decoded)
                is Set<*> -> editor.putStringSet(
                    key,
                    LinkedHashSet(decoded.map { it.toString() })
                )
                is List<*> -> editor.putStringSet(
                    key,
                    LinkedHashSet(decoded.map { it.toString() })
                )
                else -> return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
