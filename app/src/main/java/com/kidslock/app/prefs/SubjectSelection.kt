package com.kidslock.app.prefs

/**
 * 科目集合的解析/编码。家长可能在旧版或手改 prefs 里留下脏数据，
 * 这里统一过滤非法值，并在为空时回退到默认「拼音 + 数学」。
 */
object SubjectSelection {
    const val SEP = ","
    val VALID: Set<String> = linkedSetOf("pinyin", "math", "english", "ciyu")
    val ORDER: Array<String> = arrayOf("pinyin", "math", "english", "ciyu")
    val DEFAULT: Set<String> = linkedSetOf("pinyin", "math")

    fun parse(raw: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (!raw.isNullOrEmpty()) {
            for (part in raw.split(SEP)) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() && VALID.contains(trimmed)) out.add(trimmed)
            }
        }
        return if (out.isEmpty()) LinkedHashSet(DEFAULT) else out
    }

    fun encode(set: Set<String>): String {
        val sb = StringBuilder()
        for (subject in ORDER) {
            if (!set.contains(subject)) continue
            if (sb.isNotEmpty()) sb.append(SEP)
            sb.append(subject)
        }
        return sb.toString()
    }
}
