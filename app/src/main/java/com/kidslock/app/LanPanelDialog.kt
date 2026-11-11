package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 局域网家长面板的设置/地址弹窗。
 * 独立成文件，避免 MainActivity 继续膨胀；开关状态变化后由调用方刷新服务与界面。
 */
object LanPanelDialog {

    fun show(activity: Activity, pref: PrefManager, onToggled: () -> Unit) {
        val enabled = pref.isLanEnabled()
        val token = pref.getLanToken()
        val url = LanServer.url()?.let { "$it/?t=$token" }
        val status = buildString {
            if (enabled) {
                append("状态：已开启")
                append(if (LanServer.isRunning()) " · 服务运行中" else " · 服务启动中")
            } else {
                append("状态：已关闭")
            }
            append("\n\n")
            val err = LanServer.lastError
            when {
                enabled && err != null ->
                    append("服务启动失败：").append(err)
                        .append("\n\n请检查端口 8099 是否被占用后重新开启。")
                !enabled ->
                    append("开启后，同一 Wi-Fi 下的手机/电脑用浏览器就能查看状态、锁定、放行和加时。")
                url == null ->
                    append("未检测到可用的局域网地址。请先连接与孩子设备相同的 Wi-Fi，再点「刷新状态」。")
                else -> {
                    append("地址：\n").append(url).append("\n\n")
                    append("口令：").append(token).append("\n\n")
                    append("地址里的 t= 就是口令，等同家长权限，请勿告诉小孩；如泄露可重新生成。")
                }
            }
        }
        val actions = if (enabled) {
            arrayOf("复制地址", "重新生成口令（旧链接失效）", "关闭面板", "刷新状态")
        } else {
            arrayOf("开启面板", "刷新状态")
        }
        AlertDialog.Builder(activity)
            .setTitle("局域网家长面板")
            .setMessage(status)
            .setItems(actions) { _, which ->
                when (actions[which]) {
                    "开启面板" -> {
                        pref.setLanEnabled(true)
                        onToggled()
                        toast(activity, "已开启局域网面板，请确保手机与孩子设备在同一 Wi-Fi")
                        show(activity, pref, onToggled)
                    }
                    "关闭面板" -> {
                        pref.setLanEnabled(false)
                        LanServer.stop()
                        onToggled()
                        toast(activity, "已关闭局域网家长面板")
                    }
                    "重新生成口令（旧链接失效）" -> {
                        pref.regenerateLanToken()
                        toast(activity, "已重新生成口令，旧链接立即失效")
                        show(activity, pref, onToggled)
                    }
                    "复制地址" -> {
                        if (url == null) {
                            toast(activity, "未连接 Wi-Fi，暂时没有可复制的地址")
                        } else {
                            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("家长面板", url))
                            toast(activity, "地址已复制")
                        }
                    }
                    else -> show(activity, pref, onToggled)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun toast(activity: Activity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
