package com.kidslock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 首次配置向导。
 *
 * 这个应用最常见的失败不是「功能不够」，而是装完发现根本没锁住：
 * 悬浮窗没开、后台被 MIUI/EMUI 清掉、PIN 还是默认的。
 * 向导把「装完到真正生效」的步骤收拢成一张进度清单，并支持一次 10 秒试锁。
 */
object SetupWizard {
    private const val TEST_LOCK_SECONDS = 10

    private val handler = Handler(Looper.getMainLooper())

    fun show(
        activity: Activity,
        pref: PrefManager,
        onSetPin: () -> Unit,
        onChanged: () -> Unit
    ) {
        val missing = PermissionStatus.missingCount(activity)
        val battery = PermissionStatus.battery(activity)
        val pinReady = pref.isPinUsable() && !pref.shouldForcePinChange()
        val items = arrayOf(
            "1. 关键权限：" + if (missing == 0) "全部就绪" else "$missing 项未开启（点此逐项开启）",
            "2. 电池优化：" + if (battery) "已加入白名单" else "未加入，后台可能被系统清理",
            "3. 家长 PIN：" + if (pinReady) "已修改（不是默认 1234）" else "仍是默认 1234，建议现在修改",
            "4. 试锁 ${TEST_LOCK_SECONDS} 秒：验证锁屏能否盖住桌面",
            "5. 完成向导（以后可在设置页重新打开）"
        )
        val message = buildString {
            append("按顺序做完这几步，管控才会真正生效。\n\n")
            append(vendorHint())
        }
        AlertDialog.Builder(activity)
            .setTitle("首次配置向导")
            .setMessage(message)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        onChanged()
                        activity.startActivity(Intent(activity, PermissionActivity::class.java))
                    }
                    1 -> {
                        onChanged()
                        HealthCheck.launch(activity, HealthCheck.ID_BATTERY)
                    }
                    2 -> onSetPin()
                    3 -> startLockTest(activity, pref, onChanged)
                    4 -> {
                        pref.setSetupWizardDone(true)
                        onChanged()
                        Toast.makeText(activity, "向导已完成，可在设置页随时重新打开", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    /** 系统层面的后台保活提示，按厂商给不同路径，避免泛泛而谈。 */
    private fun vendorHint(): String {
        val maker = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        val path = when {
            maker.contains("xiaomi") || brand.contains("redmi") || brand.contains("xiaomi") ->
                "小米/红米：设置 → 应用 → 儿童锁屏 → 省电策略选「无限制」，并允许「自启动」。"
            maker.contains("huawei") || maker.contains("honor") || brand.contains("honor") ->
                "华为/荣耀：手机管家 → 应用启动管理 → 儿童锁屏 → 关闭「自动管理」，允许自启动/关联启动/后台活动。"
            maker.contains("oppo") || maker.contains("oneplus") || brand.contains("realme") ->
                "OPPO/一加：设置 → 电池 → 应用耗电管理 → 儿童锁屏 → 允许后台运行。"
            maker.contains("vivo") || brand.contains("iqoo") ->
                "vivo/iQOO：i管家 → 应用管理 → 权限管理 → 自启动 → 允许儿童锁屏。"
            else ->
                "请到系统设置的「应用管理 / 电池」里，允许儿童锁屏自启动并在后台运行。"
        }
        return "$path\n\n显示「全部就绪」后再做一次试锁，基本就能确认装好了。"
    }

    /**
     * 真锁 10 秒：设置锁定状态、拉起锁屏页（测试模式不显示题目），
     * 到点后自动解除。期间孩子能看到锁屏界面，正好验证是否盖得住桌面。
     */
    private fun startLockTest(activity: Activity, pref: PrefManager, onChanged: () -> Unit) {
        if (pref.isLocked()) {
            Toast.makeText(activity, "当前已经是锁定状态", Toast.LENGTH_SHORT).show()
            return
        }
        if (!PermissionStatus.overlay(activity)) {
            Toast.makeText(activity, "请先开启悬浮窗权限，否则试锁没有意义", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(activity, PermissionActivity::class.java))
            return
        }
        Diag.i("Wizard", "test lock start (${TEST_LOCK_SECONDS}s)")
        pref.setLocked(true)
        pref.setLockReason(PrefManager.REASON_MANUAL)
        pref.setHomeAliasEnabled(activity, true)
        KioskManager.syncLockState(activity)
        LockService.requestLock(activity)

        val lockIntent = Intent(activity, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(LockScreenActivity.EXTRA_TEST_SECONDS, TEST_LOCK_SECONDS)
        }
        activity.startActivity(lockIntent)
        Toast.makeText(activity, "试锁开始，${TEST_LOCK_SECONDS} 秒后自动结束", Toast.LENGTH_SHORT).show()

        val appContext = activity.applicationContext
        handler.postDelayed({
            // 兜底：即使锁屏页测试模式异常退出，也把锁定状态清掉
            if (pref.isLocked() && pref.getLockReason() == PrefManager.REASON_MANUAL) {
                pref.setLocked(false)
                pref.setHomeAliasEnabled(appContext, false)
                pref.stopTimer()
                KioskManager.syncLockState(appContext)
                LockService.requestUnlock(appContext)
                Diag.i("Wizard", "test lock auto cleared")
                Toast.makeText(appContext, "试锁结束，已自动解除", Toast.LENGTH_LONG).show()
            }
            onChanged()
        }, (TEST_LOCK_SECONDS * 1000L) + 1200L)
    }
}
