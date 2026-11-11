package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kidslock.app.prefs.AppLimitPolicy

/**
 * 应用限额的家长设置界面。
 *
 * 只使用系统 AlertDialog + 代码生成的列表，不引入新依赖；所有入口都在 PIN 门禁之后。
 * 统一走 [PrefManager] 的 AppLimitSettings，本文件不直接碰 SharedPreferences。
 */
object AppLimitDialogs {
    private const val SELF_PACKAGE = "com.kidslock.app"
    private const val CUSTOM_WEEKDAY = Int.MIN_VALUE
    private const val CUSTOM_WEEKEND = Int.MIN_VALUE + 1

    /** 不能/不应该被限额的应用：系统界面、设置、安装器与桌面（桌面由 CATEGORY_HOME 动态识别）。 */
    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.google.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller"
    )

    private data class Entry(val packageName: String, val label: String, val usedMs: Long)

    fun showManager(activity: Activity, pref: PrefManager, onChanged: () -> Unit) {
        val enabled = pref.isAppLimitEnabled()
        val entries = loadEntries(activity, pref)
        val configuredCount = entries.count { pref.hasAppLimit(it.packageName) }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8))
        }

        val status = TextView(activity).apply {
            textSize = 15f
            val base = if (enabled)
                "当前：已开启 · 已设置 $configuredCount 个应用"
            else
                "当前：未开启。开启后，超出额度的应用会被单独拦截，桌面和其他应用照常使用。"
            val notes = buildString {
                if (enabled && !PermissionStatus.usageAccess(activity)) {
                    append("\n⚠ 缺少「使用情况访问」权限，无法统计用量，限额不会生效。")
                }
                if (enabled && !PermissionStatus.overlay(activity)) {
                    append("\n⚠ 缺少悬浮窗权限：超时后会直接回到桌面，而不是显示提示页。")
                }
                append("\n周末额度默认跟随平日，可在单个应用里单独设置。")
            }
            text = base + notes
        }
        content.addView(status, matchWrap())

        if (enabled && !PermissionStatus.usageAccess(activity)) {
            content.addView(actionButton(activity, "授予使用情况访问") {
                openUsageAccess(activity)
            }, matchWrap(top = 8))
        }
        if (enabled && !PermissionStatus.overlay(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            content.addView(actionButton(activity, "授予悬浮窗权限") {
                openOverlayPermission(activity)
            }, matchWrap(top = 8))
        }

        if (entries.isEmpty()) {
            content.addView(TextView(activity).apply {
                text = if (PermissionStatus.usageAccess(activity))
                    "没有找到可管理的应用。"
                else
                    "授予「使用情况访问」后，这里会列出可以设置限额的应用。"
                textSize = 14f
                setPadding(0, dp(activity, 16), 0, dp(activity, 16))
            })
        }

        var managerDialog: AlertDialog? = null
        for (entry in entries) {
            val limit = pref.getAppLimit(entry.packageName)
            val label = buildString {
                append(entry.label)
                append("\n")
                if (limit.configured) {
                    append("平日 ").append(AppLimitPolicy.label(limit.weekdayMinutes))
                    append(" · 周末 ").append(weekendLabel(limit.weekendMinutes))
                } else {
                    append("未设置")
                }
                append(" · 今日已用 ").append(AppLimitPolicy.formatUsage(entry.usedMs))
            }
            content.addView(actionButton(activity, label) {
                managerDialog?.dismiss()
                showEditor(activity, pref, entry.packageName) { onChanged() }
            }, matchWrap(top = 6))
        }

        val scroll = ScrollView(activity).apply {
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val height = (activity.resources.displayMetrics.heightPixels * 0.68).toInt()
        managerDialog = AlertDialog.Builder(activity)
            .setTitle("应用限额（家长）")
            .setView(scroll)
            .setPositiveButton("完成") { _, _ -> }
            .setNeutralButton(if (enabled) "关闭应用限额" else "开启应用限额") { _, _ ->
                val next = !enabled
                pref.setAppLimitEnabled(next)
                if (next) startGuardService(activity)
                onChanged()
                if (next && !PermissionStatus.usageAccess(activity)) {
                    promptUsageAccess(activity)
                }
                activity.window.decorView.post { showManager(activity, pref, onChanged) }
            }
            .create()
        managerDialog?.setOnShowListener {
            managerDialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scroll.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        managerDialog?.show()
    }

    fun showEditor(
        activity: Activity,
        pref: PrefManager,
        packageName: String,
        onChanged: () -> Unit = {}
    ) {
        val label = appLabel(activity, packageName)
        val limit = pref.getAppLimit(packageName)
        val bonus = pref.getAppLimitBonusMinutes(packageName)
        val today = AppUsageTracker.todayUsageMap(activity)[packageName] ?: 0L
        val used = (today - pref.getAppLimitUsageOffsetMs(packageName)).coerceAtLeast(0L)
        val base = AppLimitPolicy.resolveMinutes(
            limit.weekdayMinutes,
            limit.weekendMinutes,
            pref.isWeekend()
        )
        val effective = AppLimitPolicy.effectiveLimitMinutes(base, bonus)
        val dayLabel = if (pref.isWeekend()) "周末" else "平日"

        val message = buildString {
            append("今日已用：").append(AppLimitPolicy.formatUsage(used)).append("\n")
            append("今天（$dayLabel）额度：").append(AppLimitPolicy.label(effective))
            if (bonus > 0) append("（含家长加时 $bonus 分钟）")
            append("\n平日：").append(AppLimitPolicy.label(limit.weekdayMinutes))
            append(" · 周末：").append(weekendLabel(limit.weekendMinutes))
            if (!pref.isAppLimitEnabled()) {
                append("\n\n⚠ 应用限额总开关未开启，保存后不会生效。")
            }
        }

        val items = arrayOf(
            "设置平日限额（当前 ${AppLimitPolicy.label(limit.weekdayMinutes)}）",
            "设置周末限额（当前 ${weekendLabel(limit.weekendMinutes)}）",
            "今日加时 10 分钟",
            "重置今日用量",
            "取消该应用限额"
        )

        val dialog = AlertDialog.Builder(activity)
            .setTitle("「$label」应用限额")
            .setMessage(message)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showLimitPicker(activity, pref, packageName, weekend = false, onChanged)
                    1 -> showLimitPicker(activity, pref, packageName, weekend = true, onChanged)
                    2 -> {
                        val currentLimit = pref.getAppLimit(packageName)
                        val currentBase = AppLimitPolicy.resolveMinutes(
                            currentLimit.weekdayMinutes,
                            currentLimit.weekendMinutes,
                            pref.isWeekend()
                        )
                        if (currentBase == AppLimitPolicy.BLOCKED) {
                            toast(activity, "「$label」当前为禁止使用，请先设置每日时长")
                            showEditor(activity, pref, packageName, onChanged)
                            return@setItems
                        }
                        pref.addAppLimitBonusMinutes(packageName, 10)
                        toast(activity, "已为「$label」加时 10 分钟")
                        onChanged()
                        showEditor(activity, pref, packageName, onChanged)
                    }
                    3 -> {
                        val offset = pref.getAppLimitUsageOffsetMs(packageName) + used
                        pref.setAppLimitUsageOffsetMs(packageName, offset)
                        toast(activity, "已重置「$label」今日用量")
                        onChanged()
                        showEditor(activity, pref, packageName, onChanged)
                    }
                    else -> {
                        pref.clearAppLimit(packageName)
                        toast(activity, "已取消「$label」的限额")
                        onChanged()
                    }
                }
            }
            .setNegativeButton("关闭") { _, _ -> }
            .create()
        dialog.show()
    }

    private fun showLimitPicker(
        activity: Activity,
        pref: PrefManager,
        packageName: String,
        weekend: Boolean,
        onChanged: () -> Unit
    ) {
        val limit = pref.getAppLimit(packageName)
        val label = appLabel(activity, packageName)

        val presets = mutableListOf<Pair<String, Int>>()
        if (weekend) {
            presets.add("跟随平日（当前 ${AppLimitPolicy.label(limit.weekdayMinutes)}）" to -1)
        } else {
            presets.add("不限" to AppLimitPolicy.UNLIMITED)
        }
        presets.add("禁止使用（0 分钟）" to 0)
        presets.add("15 分钟" to 15)
        presets.add("30 分钟" to 30)
        presets.add("45 分钟" to 45)
        presets.add("60 分钟" to 60)
        presets.add("90 分钟" to 90)
        presets.add("120 分钟" to 120)
        if (!weekend) {
            presets.add("自定义分钟数…" to CUSTOM_WEEKDAY)
        } else {
            presets.add("自定义分钟数…" to CUSTOM_WEEKEND)
        }

        val names = presets.map { it.first }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("「$label」${if (weekend) "周末" else "平日"}限额")
            .setItems(names) { _, which ->
                val value = presets[which].second
                when (value) {
                    CUSTOM_WEEKDAY, CUSTOM_WEEKEND ->
                        showCustomMinutes(activity, pref, packageName, weekend, onChanged)
                    else -> {
                        applyLimit(activity, pref, packageName, weekend, value)
                        onChanged()
                        showEditor(activity, pref, packageName, onChanged)
                    }
                }
            }
            .setNegativeButton("取消") { _, _ -> }
            .show()
    }

    private fun showCustomMinutes(
        activity: Activity,
        pref: PrefManager,
        packageName: String,
        weekend: Boolean,
        onChanged: () -> Unit
    ) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0 ~ 1440，0 表示禁止使用"
            setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (weekend) "周末限额（分钟）" else "平日限额（分钟）")
            .setView(input)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消") { _, _ -> }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = input.text.toString().trim().toIntOrNull()
                if (minutes == null || minutes < 0 || minutes > AppLimitPolicy.MAX_MINUTES) {
                    toast(activity, "请输入 0 ~ 1440 之间的分钟数")
                    return@setOnClickListener
                }
                applyLimit(activity, pref, packageName, weekend, minutes)
                onChanged()
                dialog.dismiss()
                showEditor(activity, pref, packageName, onChanged)
            }
        }
        dialog.show()
    }

    private fun applyLimit(
        activity: Activity,
        pref: PrefManager,
        packageName: String,
        weekend: Boolean,
        value: Int
    ) {
        val limit = pref.getAppLimit(packageName)
        val weekday = if (weekend) limit.weekdayMinutes else value
        val weekendMinutes = if (weekend) value else limit.weekendMinutes
        pref.setAppLimit(packageName, weekday, weekendMinutes)
        val label = appLabel(activity, packageName)
        val scope = if (weekend) "周末" else "平日"
        toast(activity, "「$label」${scope}限额：" + if (weekend && value < 0)
            "跟随平日" else AppLimitPolicy.label(value))
    }

    // ---- 应用列表 ----

    private fun loadEntries(activity: Activity, pref: PrefManager): List<Entry> {
        val pm = activity.packageManager
        val usage = AppUsageTracker.todayUsageMap(activity)
        val homePackages = queryPackages(pm, Intent.ACTION_MAIN, Intent.CATEGORY_HOME)
        val out = ArrayList<Entry>()
        val seen = HashSet<String>()
        for (info in queryLaunchable(pm)) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == SELF_PACKAGE || pkg in PROTECTED_PACKAGES || pkg in homePackages) continue
            if (!seen.add(pkg)) continue
            val label = try {
                info.loadLabel(pm).toString().ifEmpty { pkg }
            } catch (e: Exception) {
                pkg
            }
            val raw = usage[pkg] ?: 0L
            val used = (raw - pref.getAppLimitUsageOffsetMs(pkg)).coerceAtLeast(0L)
            out.add(Entry(pkg, label, used))
        }
        return out.sortedWith(
            compareByDescending<Entry> { pref.hasAppLimit(it.packageName) }
                .thenByDescending { it.usedMs }
                .thenBy { it.label.lowercase() }
        )
    }

    @Suppress("DEPRECATION")
    private fun queryLaunchable(pm: PackageManager): List<android.content.pm.ResolveInfo> {
        val out = ArrayList<android.content.pm.ResolveInfo>()
        for (category in listOf(
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_LEANBACK_LAUNCHER
        )) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            try {
                out.addAll(pm.queryIntentActivities(intent, 0))
            } catch (e: Exception) {
                // 某些电视固件不支持 LEANBACK 查询，忽略即可
            }
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun queryPackages(pm: PackageManager, action: String, category: String): Set<String> {
        val intent = Intent(action).addCategory(category)
        return try {
            pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString().ifEmpty { packageName }
    } catch (e: Exception) {
        packageName
    }

    // ---- 权限跳转 ----

    private fun openUsageAccess(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            toast(activity, "无法打开使用情况访问设置，请到系统设置里手动授予")
        }
    }

    private fun openOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.packageName)
                )
            )
        } catch (e: Exception) {
            toast(activity, "无法打开悬浮窗设置，请到系统设置里手动授予")
        }
    }

    private fun promptUsageAccess(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要「使用情况访问」")
            .setMessage("应用限额依赖系统使用情况统计来判断每个应用今天用了多久。未授予时无法统计，限额会失效。")
            .setPositiveButton("去授予") { _, _ -> openUsageAccess(activity) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun startGuardService(activity: Activity) {
        try {
            val intent = Intent(activity, LockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
        } catch (e: Exception) {
            toast(activity, "启动应用限额守护失败，请重新打开本页")
        }
    }

    // ---- 小工具 ----

    private fun weekendLabel(minutes: Int): String =
        if (minutes < 0) "跟随平日" else AppLimitPolicy.label(minutes)

    private fun actionButton(activity: Activity, text: String, onClick: () -> Unit): Button =
        Button(activity).apply {
            this.text = text
            isAllCaps = false
            isSingleLine = false
            maxLines = 3
            textSize = 14f
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10))
            minHeight = dp(activity, 48)
            setBackgroundResource(R.drawable.btn_settings_bg)
            setTextColor(activity.getColorCompat(R.color.btn_text))
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = top }

    private fun dp(context: Context, value: Int): Int =
        (context.resources.displayMetrics.density * value + 0.5f).toInt()

    private fun toast(activity: Activity, text: String) {
        try {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // 页面已销毁时忽略
        }
    }

    private fun Activity.getColorCompat(id: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, id)
}
