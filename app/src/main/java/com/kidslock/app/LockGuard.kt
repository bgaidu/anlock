package com.kidslock.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
        private const val CHECK_INTERVAL_MS = 1_000L
        private const val CHECK_INTERVAL_COVERED_MS = 5_000L
        private const val PULL_MIN_INTERVAL_MS = 1_500L
    }

    interface GuardCallback {
        fun onLockScreenPulled()
        fun onWatchdogStopped()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var watchdogRunnable: Runnable? = null
    private var lastGuardianCheckElapsed: Long = 0
    private var lastPullElapsed: Long = 0

    fun start() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        lastPullElapsed = 0
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!pref.isLocked()) {
                    Log.i(TAG, "Unlocked, watchdog stopping")
                    callback.onWatchdogStopped()
                    return
                }
                ensureAccessibilityGuardian()

                // 熄屏时不拉起锁屏，避免反复亮屏耗电
                if (powerManager?.isInteractive == false) {
                    handler.postDelayed(this, CHECK_INTERVAL_COVERED_MS)
                    return
                }

                // overlay 已经盖住时降频：设备已被挡住，没必要每 800ms 拉一次 Activity
                val covered = LockService.runningInstance?.isOverlayShowing() == true
                val interval = if (covered) CHECK_INTERVAL_COVERED_MS else CHECK_INTERVAL_MS

                if (LockScreenActivity.isOnTop()) {
                    handler.postDelayed(this, interval)
                    return
                }

                val now = SystemClock.elapsedRealtime()
                if (!LockScreenActivity.isStarting() && now - lastPullElapsed >= PULL_MIN_INTERVAL_MS) {
                    lastPullElapsed = now
                    Log.i(TAG, "Lock screen not on top, trying to pull (covered=$covered)")
                    callback.onLockScreenPulled()
                }
                handler.postDelayed(this, interval)
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
