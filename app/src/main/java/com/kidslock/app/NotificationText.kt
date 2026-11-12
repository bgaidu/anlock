package com.kidslock.app

/**
 * 常驻通知的纯文案逻辑（不依赖 Android，便于 JVM 单测）。
 *
 * 剩余时间只按分钟显示，让通知正文大约每分钟才变化一次；[LockService] 再据此
 * 跳过内容没变的 notify()，避免每秒刷新触发系统/厂商的通知限流
 * （例如荣耀日志 `reportNotification failed, more than 2000`）。
 */
object NotificationText {

    /** 剩余时间文案：向上取整到分钟，避免还剩几十秒时显示“0 分钟”。 */
    fun remaining(millis: Long): String {
        if (millis <= 0L) return "剩余观看时间不足 1 分钟"
        val minutes = (millis + 59_999L) / 60_000L
        return "剩余观看时间：约 $minutes 分钟"
    }
}

/**
 * 通知刷新去重：只有正文变化时才真正 notify()。
 *
 * 状态切换（锁定/解锁/熄屏暂停等）会改变正文，因此仍会立即刷新；
 * 计时中的剩余分钟数不变时则静默跳过。
 */
class NotificationRefreshGate {

    var lastText: String? = null
        private set

    /** 正文与上次不同则记录并返回 true；相同则返回 false。 */
    fun shouldRefresh(text: String): Boolean {
        if (text == lastText) return false
        lastText = text
        return true
    }

    /** 强制认为正文已是最新（例如 startForeground 已经发出过）。 */
    fun reset(text: String? = null) {
        lastText = text
    }
}
