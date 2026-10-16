package com.kidslock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

class TimerManager(
    private val context: Context,
    private val pref: PrefManager,
    private val callback: TimerCallback
) {
    companion object {
        private const val TAG = "LockService"
        private const val PERSIST_EVERY_TICKS = 5
        private const val ALARM_REQUEST_CODE = 2001

        @JvmStatic
        fun scheduleExpiryAlarm(context: Context, remainingMillis: Long) {
            if (remainingMillis <= 0) return
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_TIMER_CHECK)
            val pi = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + remainingMillis
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                Log.i(TAG, "Expiry alarm scheduled in ${remainingMillis / 1000}s")
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                Log.w(TAG, "Exact alarm denied, using inexact", e)
            }
        }
    }

    interface TimerCallback {
        fun onTick(remainingMillis: Long)
        fun onExpired()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var remainingMillis: Long = 0
    private var lastTickElapsed: Long = 0
    private var persistCountdown: Int = 0
    private var screenOn = true
    private var screenReceiver: BroadcastReceiver? = null
    private var tickRunnable: Runnable? = null

    fun start() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        pref.deductOfflineIfAny()
        remainingMillis = pref.getRemainingMillis()
        scheduleExpiryAlarm(context, remainingMillis)
        lastTickElapsed = SystemClock.elapsedRealtime()
        persistCountdown = 0
        tickRunnable = object : Runnable {
            override fun run() {
                if (pref.isLocked()) {
                    stop()
                    return
                }
                val now = SystemClock.elapsedRealtime()
                val dt = now - lastTickElapsed
                lastTickElapsed = now
                if (dt > 0 && screenOn) remainingMillis -= dt
                if (remainingMillis <= 0) {
                    callback.onExpired()
                    return
                }
                if (--persistCountdown <= 0) {
                    pref.persistTimer(remainingMillis)
                    persistCountdown = PERSIST_EVERY_TICKS
                }
                callback.onTick(remainingMillis)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }

    fun stop() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    fun registerScreenListener() {
        screenOn = powerManager?.isInteractive ?: true
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOn = false
                    Intent.ACTION_SCREEN_ON -> screenOn = true
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            context.registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "registerScreenListener failed", e)
        }
    }

    fun unregisterScreenListener() {
        screenReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        screenReceiver = null
    }

    fun getRemainingMillis(): Long = remainingMillis

    fun isScreenOn(): Boolean = screenOn
}
