package com.kidslock.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量诊断日志：写在应用私有目录，家长不用连 adb 也能看到「什么时候锁的、为什么没锁住」。
 *
 * - 单文件滚动：超过 256KB 自动切到 .1（只保留一份历史）；
 * - 同步写，日志量很小（只在状态变化时写），不会影响计时精度；
 * - 绝不写 PIN、密钥等敏感信息。
 */
object Diag {

    private const val FILE_NAME = "kidslock.log"
    private const val BACKUP_NAME = "kidslock.log.1"
    private const val MAX_BYTES = 256 * 1024L
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun i(module: String, message: String) = write("I", module, message, null)

    fun w(module: String, message: String, error: Throwable? = null) =
        write("W", module, message, error)

    fun e(module: String, message: String, error: Throwable? = null) =
        write("E", module, message, error)

    fun read(context: Context, limit: Int = 300): List<String> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val lines = file.readLines()
            if (lines.size <= limit) lines else lines.takeLast(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try {
                logFile(context).delete()
                backupFile(context).delete()
            } catch (_: Exception) {
            }
        }
    }

    fun path(context: Context): String = logFile(context).absolutePath

    private fun logFile(context: Context) =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun backupFile(context: Context) =
        File(context.applicationContext.filesDir, BACKUP_NAME)

    private fun write(level: String, module: String, message: String, error: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(module)
            append(": ")
            append(message)
            if (error != null) {
                append(" | ")
                append(error.javaClass.simpleName)
                append(": ")
                append(error.message ?: "")
            }
        }
        android.util.Log.println(
            when (level) {
                "E" -> android.util.Log.ERROR
                "W" -> android.util.Log.WARN
                else -> android.util.Log.INFO
            },
            "KidsLock", "$module: $message"
        )
        val context = AppContextHolder.context ?: return
        synchronized(lock) {
            try {
                val file = logFile(context)
                if (file.length() > MAX_BYTES) {
                    val backup = backupFile(context)
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(line + "\n")
            } catch (_: Exception) {
            }
        }
    }
}

/** Application 启动时注入 Context，让 [Diag] 在任意位置都能落盘。 */
object AppContextHolder {
    @Volatile
    var context: Context? = null
}
