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
        Log.i(TAG, "Service connected (boot or rebinding), running recovery check")
        recoverIfNeeded()
        handler.removeCallbacks(periodicRecovery)
        handler.postDelayed(periodicRecovery, PERIODIC_CHECK_MS)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(periodicRecovery)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        suppressForeignWindow(event)
        suppressSettings(event)
        recoverIfNeeded()
    }

    private fun suppressForeignWindow(event: AccessibilityEvent) {
        try {
            val pref = PrefManager(this)
            if (!pref.isLocked()) return
            val pkg = event.packageName ?: return
            if ("com.kidslock.app" == pkg) return
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
        try {
            startService(Intent(this, LockService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "start service failed", e)
        }
    }

    override fun onInterrupt() {
    }
}
