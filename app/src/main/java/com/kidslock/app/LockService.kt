package com.kidslock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class LockService : Service() {
    companion object {
        private const val TAG = "LockService"
        private const val CHANNEL_ID = "kids_lock_timer"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_UNLOCK = "com.kidslock.app.ACTION_UNLOCK"
        const val ACTION_HOLD_OVERLAY = "com.kidslock.app.ACTION_HOLD_OVERLAY"

        @Volatile
        var runningInstance: LockService? = null

        @JvmStatic
        fun requestUnlock(context: Context) {
            val intent = Intent(context, LockService::class.java)
            intent.action = ACTION_UNLOCK
            context.startService(intent)
        }
    }

    private lateinit var pref: PrefManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerManager: TimerManager
    private lateinit var lockGuard: LockGuard
    private lateinit var overlayManager: LockOverlayManager

    override fun onCreate() {
        super.onCreate()
        pref = PrefManager(this)
        timerManager = TimerManager(this, pref, object : TimerManager.TimerCallback {
            override fun onTick(remainingMillis: Long) {
                updateNotification()
            }
            override fun onExpired() {
                triggerLock()
            }
        })
        lockGuard = LockGuard(this, pref, object : LockGuard.GuardCallback {
            override fun onLockScreenPulled() {
                startLockScreenActivity()
            }
            override fun onWatchdogStopped() {
                overlayManager.hideOverlay()
                stopForeground(true)
                stopSelf()
            }
        })
        overlayManager = LockOverlayManager(this, object : LockOverlayManager.OverlayCallback {
            override fun onLockScreenPulled() {
                // 用户点击 overlay 上的解锁入口：先撤掉 overlay，再立即拉起答题界面
                startLockScreenActivity(keepOverlay = false)
            }
        })
        timerManager.registerScreenListener()
        createNotificationChannel()
        runningInstance = this
        KioskManager.applyOwnerPolicies(this)
        KioskManager.syncLockState(this)
        pref.deductOfflineIfAny()
        Log.i(TAG, "LockService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(pref.getRemainingMillis()))
        updateNotification()

        if (intent != null && ACTION_UNLOCK == intent.action) {
            unlockAndStop()
            return START_STICKY
        }

        if (intent != null && ACTION_HOLD_OVERLAY == intent.action) {
            if (!LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
                overlayManager.showOverlayIfPermitted()
            }
            return START_STICKY
        }

        if (pref.isLocked()) {
            Log.i(TAG, "Device locked, starting lock watchdog")
            lockGuard.start()
            if (!LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
                overlayManager.showOverlayIfPermitted()
            }
            return START_STICKY
        }

        if (!pref.isTimerActive()) {
            Log.i(TAG, "Timer not active, stopping service")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (pref.isTimerExpired()) {
            Log.i(TAG, "Timer expired, locking now")
            triggerLock()
            return START_STICKY
        }

        timerManager.start()
        return START_STICKY
    }

    private fun triggerLock() {
        Log.i(TAG, "Triggering lock screen!")
        pref.setLocked(true)
        pref.setHomeAliasEnabled(this, true)
        KioskManager.syncLockState(this)
        pref.stopTimer()
        updateNotification()
        overlayManager.showOverlayIfPermitted()
        startLockScreenActivity()
        lockGuard.start()
    }

    private fun startLockScreenActivity(keepOverlay: Boolean = true) {
        if (keepOverlay) {
            // 先保持 overlay 覆盖，等 LockScreenActivity.onResume 确认已在前台后再由它撤掉，
            // 避免原来"先 hideOverlay 再延迟 300ms 拉起"造成的裸奔窗口。
            overlayManager.showOverlayIfPermitted()
            handler.postDelayed({ pullLockScreen() }, 300)
        } else {
            overlayManager.hideOverlay()
            pullLockScreen()
        }
    }

    private fun pullLockScreen() {
        if (!pref.isLocked()) return
        val lockIntent = Intent(this, LockScreenActivity::class.java)
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Start LockScreenActivity failed", e)
        }
    }

    private fun unlockAndStop() {
        pref.setLocked(false)
        pref.setHomeAliasEnabled(this, false)
        KioskManager.syncLockState(this)
        pref.resetPinFailures()
        overlayManager.hideOverlay()
        lockGuard.stop()
        timerManager.start()
    }

    fun isOverlayShowing(): Boolean = overlayManager.isShowing()

    fun hideOverlay() {
        overlayManager.hideOverlay()
    }

    fun showOverlayIfPermitted() {
        overlayManager.showOverlayIfPermitted()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "儿童锁屏计时",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "观看时间计时通知"
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remaining: Long): Notification {
        val contentText = when {
            pref.isLocked() -> "设备已锁定，答对汉字即可解锁"
            !timerManager.isScreenOn() -> "熄屏暂停中 · 剩余观看时间：${formatTime(remaining)}"
            else -> "剩余观看时间：${formatTime(remaining)}"
        }
        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("儿童锁屏运行中")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(timerManager.getRemainingMillis()))
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        timerManager.stop()
        timerManager.unregisterScreenListener()
        lockGuard.stop()
        overlayManager.hideOverlay()
        if (runningInstance == this) runningInstance = null
        Log.i(TAG, "LockService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
