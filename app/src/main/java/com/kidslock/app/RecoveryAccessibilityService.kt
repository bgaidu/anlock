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
import androidx.core.content.ContextCompat

class RecoveryAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "RecoveryA11y"
        private const val RECOVERY_DEBOUNCE_MS = 3000L
        private const val PERIODIC_CHECK_MS = 30000L
        private const val SUPPRESS_DEBOUNCE_MS = 800L
        private var lastSuppress: Long = 0

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
