package com.kidslock.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class LockGuard(
    private val context: Context,
    private val pref: PrefManager,
    private val callback: GuardCallback
) {
    companion object {
        private const val TAG = "LockService"
        private const val GUARDIAN_CHECK_INTERVAL_MS = 15_000L
    }

    interface GuardCallback {
        fun onLockScreenPulled()
        fun onWatchdogStopped()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var lastGuardianCheckElapsed: Long = 0

    fun start() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!pref.isLocked()) {
                    Log.i(TAG, "Unlocked, watchdog stopping")
                    callback.onWatchdogStopped()
                    return
                }
                ensureAccessibilityGuardian()
                if (LockScreenActivity.isOnTop()) {
                    handler.postDelayed(this, 800)
                    return
                }
                if (!LockScreenActivity.isStarting()) {
                    Log.i(TAG, "Lock screen not on top, trying to pull")
                    callback.onLockScreenPulled()
                }
                handler.postDelayed(this, 800)
            }
        }
        handler.post(watchdogRunnable!!)
    }

    fun stop() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun ensureAccessibilityGuardian() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardianCheckElapsed < GUARDIAN_CHECK_INTERVAL_MS) return
        lastGuardianCheckElapsed = now
        if (!RecoveryAccessibilityService.isServiceEnabled(context)) {
            Log.w(TAG, "Accessibility guardian missing from settings, self-enabling")
            RecoveryAccessibilityService.trySelfEnable(context)
        }
    }
}
