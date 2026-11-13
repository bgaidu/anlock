package com.kidslock.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat

class RecoveryAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "RecoveryA11y"
        private const val RECOVERY_DEBOUNCE_MS = 3000L
        private const val PERIODIC_CHECK_MS = 30000L
        private const val SUPPRESS_DEBOUNCE_MS = 800L
        private const val SETTINGS_SUPPRESS_DEBOUNCE_MS = 1500L
        private const val SETTINGS_PACKAGES = "com.android.settings,com.android.tv.settings,com.google.android.tv.settings"
        private var lastSuppress: Long = 0
        private var lastSettingsSuppress: Long = 0

        @JvmStatic
        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cn = ComponentName(context, RecoveryAccessibilityService::class.java)
            for (item in enabled.split(":")) {
                val s = item.trim()
                if (s.equals(cn.flattenToString(), ignoreCase = true)
                    || s.equals(cn.flattenToShortString(), ignoreCase = true)
                ) return true
            }
            return false
        }

        @JvmStatic
        fun trySelfEnable(context: Context): Boolean {
            if (isServiceEnabled(context)) return true
            if (ContextCompat.checkSelfPermission(
                    context, "android.permission.WRITE_SECURE_SETTINGS"
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
            return try {
                val cn = ComponentName(context, RecoveryAccessibilityService::class.java)
                val flat = cn.flattenToShortString()
                var enabled = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (TextUtils.isEmpty(enabled) || "null" == enabled.trim()) {
                    enabled = flat
                } else if (!containsService(enabled, cn)) {
                    enabled = enabled.trim() + ":" + flat
                }
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1
                )
                Log.i(TAG, "Self-enabled accessibility service (WRITE_SECURE_SETTINGS held)")
                isServiceEnabled(context)
            } catch (e: Exception) {
                Log.e(TAG, "Self-enable failed", e)
                false
            }
        }

        private fun containsService(enabled: String, cn: ComponentName): Boolean {
            for (item in enabled.split(":")) {
                val s = item.trim()
                if (s.equals(cn.flattenToString(), ignoreCase = true)
                    || s.equals(cn.flattenToShortString(), ignoreCase = true)
                ) return true
            }
            return false
        }

        @Volatile
        private var instance: RecoveryAccessibilityService? = null

        /** 应用限额兜底：悬浮窗不可用时用无障碍回到桌面。 */
        @JvmStatic
        fun goHome(context: Context): Boolean {
            val service = instance ?: return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (e: Exception) {
                false
            }
        }
    }

    private var lastRecoveryAttempt: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val periodicRecovery = object : Runnable {
        override fun run() {
            recoverIfNeeded()
            handler.postDelayed(this, PERIODIC_CHECK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Service connected (boot or rebinding), running recovery check")
        recoverIfNeeded()
        handler.removeCallbacks(periodicRecovery)
        handler.postDelayed(periodicRecovery, PERIODIC_CHECK_MS)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(periodicRecovery)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(periodicRecovery)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        suppressForeignWindow(event)
        suppressSettings(event)
        notifyAppLimit(event)
        recoverIfNeeded()
    }

    /** 把前台应用变化告诉 LockService，用于应用级限额的即时拦截。 */
    private fun notifyAppLimit(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android") return
        // 输入法窗口不算"使用了某个应用"，否则会往使用统计里塞一个输入法包名
        if (isInputMethodPackage(pkg)) return
        try {
            val pref = PrefManager(this)
            if (pref.isAppLimitEnabled()) LockService.notifyForeground(this, pkg)
        } catch (e: Exception) {
            Log.e(TAG, "notifyAppLimit failed", e)
        }
    }

    private fun suppressForeignWindow(event: AccessibilityEvent) {
        try {
            val pref = PrefManager(this)
            if (!pref.isLocked()) return
            val pkg = event.packageName?.toString() ?: return
            if ("com.kidslock.app" == pkg) return
            // 软键盘不是"外部应用"：填空题必须靠它输入。
            // 以前这里把输入法窗口当成越界窗口，孩子一点输入框就被踢回桌面 + 锁屏浮层，
            // 表现为"答题时一调软键盘就冲突、一直在锁屏"。
            if (isInputMethodPackage(pkg)) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastSuppress < SUPPRESS_DEBOUNCE_MS) return
            lastSuppress = now
            Log.i(TAG, "Foreign window while locked: $pkg, collapsing & covering")
            performGlobalAction(GLOBAL_ACTION_HOME)
            LockOverlayManager.holdOverlay(this)
        } catch (e: Exception) {
            Log.e(TAG, "suppressForeignWindow failed", e)
        }
    }

    /** 判断窗口包名是否是当前启用的输入法（含默认输入法）。 */
    private fun isInputMethodPackage(pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.systemui") return false
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val enabled = imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
            if (enabled) return true
            // 兜底：部分 ROM 的输入法不在 enabledInputMethodList 里，再比对默认输入法
            val defaultIme = Settings.Secure.getString(
                contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false
            defaultIme.substringBefore('/') == pkg
        } catch (e: Exception) {
            Log.e(TAG, "isInputMethodPackage failed", e)
            false
        }
    }

    /**
     * 管控期间拦截系统设置：孩子进入设置页会直接回到桌面。
     * 家长在设置页验证 PIN 后 2 分钟内可自由进入；权限页打开系统设置时也会自动放行。
     *
     * 只在「计时中 / 锁屏中」生效，且依赖无障碍服务识别应用切换。
     */
    private fun suppressSettings(event: AccessibilityEvent) {
        val pref = PrefManager(this)
        if (!pref.isLocked() && !pref.isTimerActive()) return
        if (pref.isSettingsAllowedNow()) return
        val pkg = event.packageName?.toString() ?: return
        if (!isSettingsPackage(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSettingsSuppress < SETTINGS_SUPPRESS_DEBOUNCE_MS) return
        lastSettingsSuppress = now
        Log.i(TAG, "Settings blocked during control: $pkg")
        Diag.w("Guard", "settings blocked: $pkg (locked=${pref.isLocked()} timer=${pref.isTimerActive()})")
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (pref.isLocked()) LockOverlayManager.holdOverlay(this)
        try {
            Toast.makeText(this, "管控期间系统设置已锁定，家长 PIN 验证后可进入", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    private fun isSettingsPackage(pkg: String): Boolean {
        if (pkg in SETTINGS_PACKAGES.split(",")) return true
        return try {
            packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                ?.activityInfo?.packageName == pkg
        } catch (_: Exception) {
            false
        }
    }

    private fun recoverIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoveryAttempt < RECOVERY_DEBOUNCE_MS) return
        lastRecoveryAttempt = now
        try {
            val pref = PrefManager(this)
            if (pref.isLocked()) {
                if (!LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
                    Log.i(TAG, "Locked but lock screen not on top, pulling it up")
                    startActivity(
                        Intent(this, LockScreenActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }
                startServiceSafely()
            } else if (pref.isTimerActive()) {
                pref.deductOfflineIfAny()
                if (pref.getRemainingMillis() <= 0) {
                    Log.i(TAG, "Timer expired while service down, pulling lock screen")
                    startActivity(
                        Intent(this, LockScreenActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } else {
                    startServiceSafely()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed", e)
        }
    }

    private fun startServiceSafely() {
        // 服务还活着就不要再 startService：onStartCommand 里的 startForeground 会重新
        // 提交一次常驻通知，30 秒一次的巡检会让通知刷新频率远超「每分钟一次」。
        if (LockService.runningInstance != null) return
        try {
            startService(Intent(this, LockService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "start service failed", e)
        }
    }

    override fun onInterrupt() {
    }
}
