package com.kidslock.app

/**
 * 设置备份的纯数据编解码，不依赖 Android Context / SharedPreferences，
 * 便于在 JVM 单测里做 round-trip（原来的 SettingsBackup 只能手测）。
 *
 * 存储格式保持 1.0.1.17 的 {t: 类型, v: 值}，老备份仍可导入。
 */
object SettingsCodec {
    const val APP_ID = "kidslock"
    const val VERSION = 1

    /** 允许导入导出的键。新增设置项时记得加到这里，否则不会被备份。 */
    @JvmField
    val ALLOWED_KEYS: Set<String> = LinkedHashSet<String>().apply {
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
        add("eye_rest_enabled")
        add("eye_rest_interval_min")
        add("schedule_enabled")
        add("schedule_free_mode")
        add("app_limit_enabled")
        for (day in 0..6) {
            add("schedule_start_$day")
            add("schedule_end_$day")
        }
        for (subject in listOf("pinyin", "math", "english", "ciyu")) {
            add("question_grade_subject_$subject")
        }
    }

    /** 按包名保存的应用限额键：只备份家长配置，不备份当日加时/重置偏移。 */
    private val ALLOWED_PREFIXES = listOf(
        "app_limit_min_",
        "app_limit_weekend_"
    )

    fun isAllowed(key: String): Boolean =
        ALLOWED_KEYS.contains(key) || ALLOWED_PREFIXES.any { key.startsWith(it) }

    data class Encoded(val type: String, val value: Any)

    /** 把 SharedPreferences 里读到的值转成可序列化的类型标记 + 纯值。 */
    fun encode(value: Any): Encoded = when (value) {
        is Boolean -> Encoded("b", value)
        is Int -> Encoded("i", value)
        is Long -> Encoded("l", value)
        is Float -> Encoded("f", value.toDouble())
        is String -> Encoded("s", value)
        is Set<*> -> Encoded("ss", value.map { it.toString() })
        else -> Encoded("s", value.toString())
    }

    /** 把备份里的类型标记还原成 SharedPreferences 支持的值；无法识别返回 null。 */
    fun decode(type: String, value: Any?): Any? = when (type) {
        "b" -> when (value) {
            is Boolean -> value
            else -> value?.toString()?.toBoolean()
        }
        "i" -> (value as? Number)?.toInt() ?: value?.toString()?.toIntOrNull()
        "l" -> (value as? Number)?.toLong() ?: value?.toString()?.toLongOrNull()
        "f" -> (value as? Number)?.toFloat() ?: value?.toString()?.toFloatOrNull()
        "s" -> value?.toString() ?: ""
        "ss" -> when (value) {
            is Iterable<*> -> value.map { it.toString() }.toCollection(LinkedHashSet())
            is Array<*> -> value.map { it.toString() }.toCollection(LinkedHashSet())
            else -> null
        }
        else -> null
    }
}
