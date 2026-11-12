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
import android.widget.Toast
import androidx.core.app.NotificationCompat

class LockService : Service() {
    companion object {
        private const val TAG = "LockService"
        private const val CHANNEL_ID = "kids_lock_timer"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_UNLOCK = "com.kidslock.app.ACTION_UNLOCK"
        private const val ACTION_LOCK = "com.kidslock.app.ACTION_LOCK"
        private const val ACTION_APP_FOREGROUND = "com.kidslock.app.ACTION_APP_FOREGROUND"
        private const val EXTRA_APP_PACKAGE = "app_package"
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

        /**
         * 无障碍服务检测到前台应用变化时调用。
         * 服务还活着就直接检查；服务不在（被系统杀掉）则按需拉起，避免应用限额失效。
         */
        @JvmStatic
        fun notifyForeground(context: Context, packageName: String) {
            if (packageName.isEmpty()) return
            val service = runningInstance
            if (service != null) {
                service.onAppForeground(packageName)
                return
            }
            if (packageName == context.packageName) return
            try {
                if (!PrefManager(context).isAppLimitEnabled()) return
                val intent = Intent(context, LockService::class.java)
                    .setAction(ACTION_APP_FOREGROUND)
                    .putExtra(EXTRA_APP_PACKAGE, packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "notifyForeground failed", e)
            }
        }
    }

    private lateinit var pref: PrefManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerManager: TimerManager
    private lateinit var lockGuard: LockGuard
    private lateinit var overlayManager: LockOverlayManager
    private lateinit var countdownOverlay: CountdownOverlay
    private lateinit var appLimitOverlay: AppLimitOverlay
    private var lastAppLimitToast = 0L
    private var lastAppLimitCount = -1
    private var lastCapabilityCheck = 0L
    private var lastMonitoring: Boolean? = null
    /** 常驻通知去重：正文（按分钟粒度）没变就不重复 notify，规避厂商通知限流。 */
    private val notificationGate = NotificationRefreshGate()
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
                checkEyeRest()
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
        appLimitOverlay = AppLimitOverlay(this, object : AppLimitOverlay.Callback {
            override fun onGoHome() {
                appLimitOverlay.hide()
                goHome()
            }

            override fun onOpenSettings(packageName: String) {
                appLimitOverlay.hide()
                openAppLimitSettings(packageName)
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
        if (pref.isAppLimitEnabled()) startAppLimitGuard()
        Diag.i("Service", "created timer=${pref.isTimerActive()} locked=${pref.isLocked()}")
        Log.i(TAG, "LockService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialRemaining = pref.getRemainingMillis()
        // startForeground 已经发出通知，先记下正文，随后的 updateNotification 不会重复 notify
        notificationGate.reset(notificationText(initialRemaining))
        startForeground(NOTIFICATION_ID, buildNotification(initialRemaining))

        if (intent != null && ACTION_APP_FOREGROUND == intent.action) {
            intent.getStringExtra(EXTRA_APP_PACKAGE)?.let { onAppForeground(it) }
            if (pref.isAppLimitEnabled()) {
                startAppLimitGuard()
            } else if (!pref.isLocked() && !pref.isTimerActive() &&
                !pref.isScheduleEnabled() && !pref.isLanEnabled()) {
                stopForeground(true)
                stopSelf()
            }
            return START_STICKY
        }

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

        if (!pref.isTimerActive() && !pref.isScheduleEnabled() &&
            !pref.isLanEnabled() && !pref.isAppLimitEnabled()) {
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
        if (pref.isAppLimitEnabled()) {
            startAppLimitGuard()
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
                if (!pref.isLocked() && !pref.isTimerActive() && !pref.isLanEnabled() &&
                    !pref.isAppLimitEnabled()) {
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

    /** 应用限额守护：每 5 秒复查一次前台应用，切换应用时由无障碍立即触发。 */
    private val appLimitTick = object : Runnable {
        override fun run() {
            if (!pref.isAppLimitEnabled()) {
                appLimitOverlay.hide()
                AppUsageTracker.onBackground()
                handler.removeCallbacks(this)
                if (!pref.isLocked() && !pref.isTimerActive() &&
                    !pref.isScheduleEnabled() && !pref.isLanEnabled()) {
                    stopForeground(true)
                    stopSelf()
                }
                return
            }
            if (pref.isLocked()) {
                appLimitOverlay.hide()
            } else {
                checkAppLimit()
            }
            val count = pref.configuredAppPackages().size
            if (count != lastAppLimitCount) {
                lastAppLimitCount = count
                updateNotification()
            }
            handler.postDelayed(this, 5000)
        }
    }

    private fun startAppLimitGuard() {
        handler.removeCallbacks(appLimitTick)
        handler.post(appLimitTick)
    }

    /** 无障碍服务报告前台应用变化：立即结算会话并检查额度。 */
    fun onAppForeground(packageName: String) {
        if (!pref.isAppLimitEnabled()) return
        if (packageName == this.packageName) {
            // 悬浮层本身也会触发本应用的窗口事件；只有真正的 Activity 在前台时才让路
            if (ForegroundUi.isVisible) {
                appLimitOverlay.hide()
                AppUsageTracker.onBackground()
            }
            return
        }
        AppUsageTracker.onForeground(this, packageName)
        if (pref.isLocked()) {
            appLimitOverlay.hide()
            return
        }
        checkAppLimit()
    }

    private fun checkAppLimit() {
        if (!pref.isAppLimitEnabled()) {
            appLimitOverlay.hide()
            AppUsageTracker.onBackground()
            return
        }
        if (ForegroundUi.isVisible) {
            // 家长在本应用设置页/权限页/锁屏页：不拦截，也不把时间算到别的应用上
            if (appLimitOverlay.isShowing()) appLimitOverlay.hide()
            AppUsageTracker.onBackground()
            return
        }
        val actual = AppUsageTracker.currentPackage()
            ?: ForegroundApp.current(this, includeSelf = true)?.packageName
        val pkg = actual ?: appLimitOverlay.shownPackage()
        if (pkg == this.packageName) return
        if (pkg.isNullOrEmpty() || pkg == "android" || isHomePackage(pkg)) {
            if (appLimitOverlay.isShowing()) appLimitOverlay.hide()
            AppUsageTracker.onBackground()
            return
        }
        val state = AppLimitRuntime.state(this, pref, pkg)
        if (state == null || !state.blocked) {
            if (appLimitOverlay.isShowing()) {
                appLimitOverlay.hide()
                // 家长刚加时/重置：拦截层撤掉后重新开始计入当前应用
                AppUsageTracker.onForeground(this, pkg)
            }
            return
        }
        val label = appLabel(pkg)
        if (appLimitOverlay.isShowingFor(pkg)) {
            appLimitOverlay.show(state, label)
            return
        }
        // 拦截期间不再把时间算到被拦截的应用上，避免家长加时后额度立刻又用完
        AppUsageTracker.onBackground()
        // 缺少使用情况访问时无法判断孩子是否已经切走，悬浮层可能盖住桌面；
        // 这种情况直接回桌面，保证孩子不会被自己的拦截层困住。
        if (PermissionStatus.overlay(this) && PermissionStatus.usageAccess(this)) {
            appLimitOverlay.show(state, label)
            Diag.w(
                "AppLimit",
                "blocked pkg=$pkg used=${state.usedMs / 60_000}m limit=${state.limitMinutes}m"
            )
        } else {
            appLimitOverlay.hide()
            goHome()
            val now = SystemClock.elapsedRealtime()
            if (now - lastAppLimitToast > 10_000) {
                lastAppLimitToast = now
                Toast.makeText(this, "「$label」今日额度已用完", Toast.LENGTH_SHORT).show()
                Diag.w("AppLimit", "blocked (no overlay) pkg=$pkg, bounced home")
            }
        }
    }

    private var cachedHomePackages: Set<String>? = null

    @Suppress("DEPRECATION")
    private fun isHomePackage(packageName: String): Boolean {
        val homes = cachedHomePackages ?: try {
            packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }.also { cachedHomePackages = it }
        return packageName in homes
    }

    private fun goHome() {
        if (RecoveryAccessibilityService.goHome(this)) return
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "goHome failed", e)
        }
    }

    private fun openAppLimitSettings(packageName: String) {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_APP_LIMIT_PACKAGE, packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "open app limit settings failed", e)
        }
    }

    private fun appLabel(packageName: String): String = try {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
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
        appLimitOverlay.hide()
        EyeRestOverlay.hide()
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
        if (pref.isAppLimitEnabled()) startAppLimitGuard()
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

    /** 常驻通知正文：状态不变且剩余分钟数没变时保持稳定，避免按秒刷新。 */
    private fun notificationText(remaining: Long): String {
        return when {
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
            !pref.isTimerActive() && pref.isAppLimitEnabled() ->
                "应用限额守护中 · 已设置 ${pref.configuredAppPackages().size} 个应用"
            !timerManager.isScreenOn() -> "熄屏暂停中 · ${NotificationText.remaining(remaining)}"
            else -> NotificationText.remaining(remaining)
        }
    }

    private fun buildNotification(remaining: Long): Notification {
        val contentText = notificationText(remaining)
        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 快捷加时：仍然先进设置页过 PIN，验证通过后才弹加时选项，通知本身不能绕过门禁
        val bonusIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PARENT_BONUS, true)
        }
        val bonusPendingIntent = PendingIntent.getActivity(
            this, 2, bonusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("儿童锁屏运行中")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "打开设置", pendingIntent)
            .addAction(android.R.drawable.ic_input_add, "家长加时", bonusPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 刷新常驻通知：正文按分钟粒度生成，文本未变化时直接跳过 notify()，
     * 让系统通知栏（以及厂商的通知限流统计）大约每分钟才收到一次更新。
     * 锁定/解锁/熄屏暂停等状态切换会改变正文，仍然立即刷新。
     */
    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val remaining = timerManager.getRemainingMillis()
        val text = notificationText(remaining)
        if (!notificationGate.shouldRefresh(text)) return
        nm.notify(NOTIFICATION_ID, buildNotification(remaining))
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

    /**
     * 护眼提醒：计时使用中每 N 分钟全屏提示一次。默认关闭；
     * 提醒本身只有 20 秒且不影响计时。
     */
    private fun checkEyeRest() {
        if (!pref.isEyeRestEnabled() || pref.isLocked()) return
        if (!timerManager.isScreenOn() || EyeRestOverlay.isShowing()) return
        val now = System.currentTimeMillis()
        val last = pref.eyeRestLastMs()
        if (last <= 0L || last > now) {
            pref.markEyeRestShown()
            return
        }
        val interval = pref.getEyeRestIntervalMinutes() * 60_000L
        if (now - last >= interval) {
            // 即使悬浮窗权限缺失也推进时间戳，避免每秒重试刷日志
            EyeRestOverlay.show(this)
            pref.markEyeRestShown()
        }
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
        appLimitOverlay.hide()
        AppUsageTracker.onBackground()
        countdownOverlay.destroy()
        handler.removeCallbacks(mediaTick)
        handler.removeCallbacks(scheduleTick)
        handler.removeCallbacks(appLimitTick)
        // 只在未锁屏时恢复声音：锁屏期间服务被系统杀掉时，锁屏还在，声音应该继续保持压住
        if (!pref.isLocked()) MediaPause.release(this)
        if (runningInstance == this) runningInstance = null
        Log.i(TAG, "LockService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
