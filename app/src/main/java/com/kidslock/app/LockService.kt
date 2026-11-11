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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class LockService : Service() {
    companion object {
        private const val TAG = "LockService"
        private const val CHANNEL_ID = "kids_lock_timer"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_UNLOCK = "com.kidslock.app.ACTION_UNLOCK"
        private const val ACTION_LOCK = "com.kidslock.app.ACTION_LOCK"
        const val ACTION_HOLD_OVERLAY = "com.kidslock.app.ACTION_HOLD_OVERLAY"

        @Volatile
        var runningInstance: LockService? = null

        @JvmStatic
        fun requestUnlock(context: Context) {
            val intent = Intent(context, LockService::class.java)
            intent.action = ACTION_UNLOCK
            context.startService(intent)
        }

        /** 立即锁定（家长面板 / 局域网操作使用）。 */
        @JvmStatic
        fun requestLock(context: Context) {
            val intent = Intent(context, LockService::class.java)
            intent.action = ACTION_LOCK
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "requestLock failed", e)
            }
        }
    }

    private lateinit var pref: PrefManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerManager: TimerManager
    private lateinit var lockGuard: LockGuard
    private lateinit var overlayManager: LockOverlayManager
    private lateinit var countdownOverlay: CountdownOverlay
    private var lastCapabilityCheck = 0L
    private var lastMonitoring: Boolean? = null
    private val mediaTick = object : Runnable {
        override fun run() {
            if (pref.isLocked()) {
                MediaPause.enforce(this@LockService)
                handler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        pref = PrefManager(this)
        countdownOverlay = CountdownOverlay(this)
        timerManager = TimerManager(this, pref, object : TimerManager.TimerCallback {
            override fun onTick(remainingMillis: Long) {
                updateNotification()
                updateCountdown(remainingMillis)
                checkCapabilities()
                enforceSchedule()
            }
            override fun onExpired() {
                if (pref.isScheduleEnabled() && pref.isScheduleFreeMode() && pref.isWithinSchedule()) {
                    // 仅时段限制：时段内到点也不锁，直接结束本次计时
                    Diag.i("Timer", "expired inside free window, no lock")
                    pref.stopTimer()
                    MediaPause.release(this@LockService)
                    updateNotification()
                    startScheduleGuard()
                    return
                }
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
        GuardNotifier.check(this)
        Revival.schedule(this)
        Diag.i("Service", "created timer=${pref.isTimerActive()} locked=${pref.isLocked()}")
        Log.i(TAG, "LockService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(pref.getRemainingMillis()))
        updateNotification()

        if (intent != null && ACTION_UNLOCK == intent.action) {
            unlockAndStop()
            return START_STICKY
        }

        if (intent != null && ACTION_LOCK == intent.action) {
            if (!pref.isLocked()) {
                triggerLock()
                // triggerLock 默认标记为"时间用完"，这里纠正成家长手动锁定，
                // 否则仅时段限制模式会在下一个时段 tick 把它自动放行
                pref.setLockReason(PrefManager.REASON_MANUAL)
                return START_STICKY
            }
            // 已经锁定则继续走下面的锁定流程，补起守护/锁屏页
        }

        if (pref.isLanEnabled()) LanServer.start(this)

        if (intent != null && ACTION_HOLD_OVERLAY == intent.action) {
            if (!LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
                overlayManager.showOverlayIfPermitted()
            }
            if (pref.isScheduleEnabled() || pref.isLocked()) startScheduleGuard()
            return START_STICKY
        }

        if (pref.isLocked()) {
            Log.i(TAG, "Device locked, starting lock watchdog")
            countdownOverlay.hide()
            MediaPause.pause(this)
            handler.removeCallbacks(mediaTick)
            handler.postDelayed(mediaTick, 3000)
            lockGuard.start()
            startScheduleGuard()
            if (!LockScreenActivity.isOnTop() && !LockScreenActivity.isStarting()) {
                overlayManager.showOverlayIfPermitted()
            }
            return START_STICKY
        }

        if (!pref.isTimerActive() && !pref.isScheduleEnabled() && !pref.isLanEnabled()) {
            Log.i(TAG, "Timer not active and schedule off, stopping service")
            countdownOverlay.hide()
            Revival.cancel(this)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (pref.isTimerExpired()) {
            Log.i(TAG, "Timer expired, locking now")
            triggerLock()
            return START_STICKY
        }

        if (pref.isTimerActive()) {
            timerManager.start()
            enforceSchedule()
            updateCountdown(pref.getRemainingMillis())
        }
        if (pref.isScheduleEnabled()) {
            // 常驻守护：即使没有在计时，也按时段锁屏 / 到点自动放行
            startScheduleGuard()
            updateNotification()
        }
        return START_STICKY
    }

    /** 使用时段外强制锁屏（家长 PIN 可临时放行）。 */
    private fun enforceSchedule() {
        if (pref.isLocked()) return
        if (!pref.isScheduleEnabled()) return
        if (pref.isWithinSchedule()) return
        Diag.i("Schedule", "outside allowed window, locking")
        triggerLock(bySchedule = true)
    }

    private val scheduleTick = object : Runnable {
        override fun run() {
            if (!pref.isScheduleEnabled()) {
                // 家长关掉时段限制后，不再常驻
                ScheduleAlarm.cancel(this@LockService)
                if (!pref.isLocked() && !pref.isTimerActive() && !pref.isLanEnabled()) {
                    handler.removeCallbacks(this)
                    stopForeground(true)
                    stopSelf()
                }
                return
            }
            if (pref.isLocked()) {
                val shouldRelease = if (pref.isScheduleFreeMode()) {
                    // 仅时段限制：时段内释放时段锁/计时锁，但保留家长手动「立即锁定」
                    pref.isWithinSchedule() &&
                            pref.getLockReason() != PrefManager.REASON_MANUAL
                } else {
                    pref.isWithinSchedule() && pref.isLockedBySchedule() &&
                            (pref.effectiveUnlockMinutes() > 0 ||
                                    (pref.isTimerActive() && pref.getRemainingMillis() > 0))
                }
                if (shouldRelease) releaseScheduleLock()
            } else if (!pref.isWithinSchedule()) {
                triggerLock(bySchedule = true)
            }
            if (pref.isScheduleEnabled() || pref.isLocked() || pref.isTimerActive()) {
                handler.postDelayed(this, 5000)
            }
        }
    }

    private fun startScheduleGuard() {
        handler.removeCallbacks(scheduleTick)
        handler.post(scheduleTick)
        ScheduleAlarm.schedule(this)
    }

    /** 时段恢复：自动解除由时段触发的锁屏，并按当前额度开始一次计时。 */
    private fun releaseScheduleLock() {
        Diag.i("Schedule", "within window again, auto-release")
        pref.setLocked(false)
        pref.setHomeAliasEnabled(this, false)
        KioskManager.syncLockState(this)
        overlayManager.hideOverlay()
        lockGuard.stop()
        MediaPause.release(this)
        handler.removeCallbacks(mediaTick)
        if (pref.isScheduleFreeMode()) {
            // 仅时段限制：时段内自由使用，不恢复/新开计时
            pref.stopTimer()
        } else if (pref.isTimerActive() && pref.getRemainingMillis() > 0) {
            // 优先恢复被时段锁打断的剩余时长；没有剩余才按额度开新的一段
            timerManager.start()
            updateCountdown(pref.getRemainingMillis())
        } else {
            val minutes = pref.effectiveUnlockMinutes()
            if (minutes > 0) {
                pref.consumeBonusMinutes()
                pref.startTimer(minutes)
                Revival.schedule(this)
                timerManager.start()
                updateCountdown(pref.getRemainingMillis())
            }
        }
        updateNotification()
    }

    private fun triggerLock(bySchedule: Boolean = false) {
        Log.i(TAG, "Triggering lock screen! schedule=$bySchedule")
        Diag.i("Lock", if (bySchedule) "triggered by schedule" else "triggered")
        countdownOverlay.hide()
        MediaPause.pause(this)
        handler.removeCallbacks(mediaTick)
        handler.postDelayed(mediaTick, 3000)
        pref.setLocked(true)
        pref.setLockReason(
            if (bySchedule) PrefManager.REASON_SCHEDULE else PrefManager.REASON_TIME
        )
        pref.setHomeAliasEnabled(this, true)
        KioskManager.syncLockState(this)
        // 时段锁定不销毁本次剩余时长，时段恢复后接着用
        if (!bySchedule) pref.stopTimer()
        updateNotification()
        overlayManager.showOverlayIfPermitted()
        startLockScreenActivity()
        lockGuard.start()
        startScheduleGuard()
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
        Diag.i("Lock", "unlocked")
        pref.setLocked(false)
        pref.setHomeAliasEnabled(this, false)
        KioskManager.syncLockState(this)
        pref.resetPinFailures()
        handler.removeCallbacks(mediaTick)
        MediaPause.release(this)
        overlayManager.hideOverlay()
        lockGuard.stop()
        timerManager.start()
        updateCountdown(pref.getRemainingMillis())
        startScheduleGuard()
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
            pref.isLocked() && pref.isLockedBySchedule() -> {
                val day = pref.scheduleDayIndex()
                "已到休息时段（今天 ${pref.scheduleWindowLabel(day)}），家长可用 PIN 放行"
            }
            pref.isLocked() -> "设备已锁定，答对汉字即可解锁"
            !pref.isTimerActive() && pref.isScheduleEnabled() -> {
                val day = pref.scheduleDayIndex()
                when {
                    pref.isWithinSchedule() && pref.isScheduleFreeMode() ->
                        "时段内自由使用（仅时段限制）· 今天 ${pref.scheduleWindowLabel(day)}"
                    pref.isWithinSchedule() ->
                        "使用时段守护中 · 今天 ${pref.scheduleWindowLabel(day)}"
                    else ->
                        "当前不在使用时段 · 今天 ${pref.scheduleWindowLabel(day)}"
                }
            }
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

    /**
     * 角标显示条件：家长已开启 + 计时中 + 未锁屏 + 屏幕亮着 + 不在本应用设置页 / 锁屏页。
     * 任何一条不满足都立刻隐藏，避免锁屏后角标压在答题界面上。
     */
    private fun updateCountdown(remainingMillis: Long) {
        val show = pref.isShowCountdown() &&
                pref.isTimerActive() &&
                !pref.isLocked() &&
                remainingMillis > 0 &&
                timerManager.isScreenOn() &&
                !MainActivity.isVisible &&
                !LockScreenActivity.isOnTop()
        if (show) {
            countdownOverlay.show(remainingMillis)
        } else {
            countdownOverlay.hide()
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /** 每 30 秒检查一次拦截能力，权限被系统收回时主动通知家长（只提示一次）。 */
    private fun checkCapabilities() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCapabilityCheck < 30_000) return
        lastCapabilityCheck = now
        val overlay = PermissionStatus.overlay(this)
        val usage = PermissionStatus.usageAccess(this)
        val accessibility = PermissionStatus.accessibility(this)
        val monitoring = overlay || usage || accessibility
        if (monitoring != lastMonitoring) {
            val line = "monitoring=$monitoring overlay=$overlay usage=$usage accessibility=$accessibility"
            if (monitoring) Diag.i("Guard", line) else Diag.w("Guard", line)
            lastMonitoring = monitoring
        }
        GuardNotifier.check(this)
    }

    override fun onDestroy() {
        Diag.i("Service", "destroyed")
        LanServer.stop()
        timerManager.stop()
        timerManager.unregisterScreenListener()
        lockGuard.stop()
        overlayManager.hideOverlay()
        countdownOverlay.destroy()
        handler.removeCallbacks(mediaTick)
        handler.removeCallbacks(scheduleTick)
        // 只在未锁屏时恢复声音：锁屏期间服务被系统杀掉时，锁屏还在，声音应该继续保持压住
        if (!pref.isLocked()) MediaPause.release(this)
        if (runningInstance == this) runningInstance = null
        Log.i(TAG, "LockService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
