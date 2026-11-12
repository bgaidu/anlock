package com.kidslock.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 家长操作审计日志：持久化记录「谁在什么时候做了什么」。
 *
 * - 与 [Diag] 分开：诊断日志面向排障，可能很吵；这里只记家长主动操作（加时、放行、改设置等）；
 * - 单文件环形保留最近 [MAX_LINES] 条，孩子长期使用也不会无限增长；
 * - 绝不写 PIN、密钥等敏感信息。
 */
object AuditLog {

    private const val FILE_NAME = "parent_audit.log"
    private const val MAX_LINES = 200
    private val lock = Any()

    /** 记录一条家长操作。detail 中的换行会被压成空格，保证一行一条。 */
    fun record(context: Context, source: String, action: String, detail: String = "") {
        val line = formatLine(System.currentTimeMillis(), source, action, detail)
        synchronized(lock) {
            try {
                val file = logFile(context)
                val lines = if (file.exists()) {
                    file.readLines().toMutableList()
                } else {
                    ArrayList<String>()
                }
                lines.add(line)
                while (lines.size > MAX_LINES) lines.removeAt(0)
                file.writeText(lines.joinToString("\n") + "\n")
            } catch (_: Exception) {
            }
        }
        Diag.i("Audit", "$source/$action${if (detail.isBlank()) "" else ": $detail"}")
    }

    /** 纯格式化，便于 JVM 单测；每次新建 SimpleDateFormat，避免跨线程复用问题。 */
    internal fun formatLine(timeMillis: Long, source: String, action: String, detail: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            append(format.format(Date(timeMillis)))
            append(" | ")
            append(source)
            append(" | ")
            append(action)
            if (detail.isNotBlank()) {
                append(" | ")
                append(detail.replace('\n', ' ').replace('\r', ' '))
            }
        }
    }

    /** 按写入顺序返回最近 [limit] 条（最早在前，方便直接导出）。 */
    fun read(context: Context, limit: Int = MAX_LINES): List<String> {
        synchronized(lock) {
            val file = logFile(context)
            if (!file.exists()) return emptyList()
            return try {
                val lines = file.readLines().filter { it.isNotBlank() }
                if (lines.size <= limit) lines else lines.takeLast(limit)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try {
                logFile(context).delete()
            } catch (_: Exception) {
            }
        }
    }

    fun path(context: Context): String = logFile(context).absolutePath

    private fun logFile(context: Context) =
        File(context.applicationContext.filesDir, FILE_NAME)
}
