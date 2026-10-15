package com.kidslock.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public final class TimerManager {
    private static final String TAG = "LockService";
    private static final String CHANNEL_ID = "kids_lock_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_UNLOCK = "com.kidslock.app.ACTION_UNLOCK";
    private static final int PERSIST_EVERY_TICKS = 5;
    private static final int ALARM_REQUEST_CODE = 2001;

    public interface TimerCallback {
        void onTick(long remainingMillis);
        void onExpired();
    }

    private final Context context;
    private final PrefManager pref;
    private final Handler handler;
    private final PowerManager powerManager;
    private final TimerCallback callback;

    private long remainingMillis;
    private long lastTickElapsed;
    private int persistCountdown;
    private boolean screenOn = true;
    private BroadcastReceiver screenReceiver;
    private Runnable tickRunnable;

    public TimerManager(Context context, PrefManager pref, TimerCallback callback) {
        this.context = context.getApplicationContext();
        this.pref = pref;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    public void start() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
        pref.deductOfflineIfAny();
        remainingMillis = pref.getRemainingMillis();
        scheduleExpiryAlarm(context, remainingMillis);
        lastTickElapsed = SystemClock.elapsedRealtime();
        persistCountdown = 0;
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (pref.isLocked()) {
                    stop();
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                long dt = now - lastTickElapsed;
                lastTickElapsed = now;
                if (dt > 0 && screenOn) remainingMillis -= dt;
                if (remainingMillis <= 0) {
                    callback.onExpired();
                    return;
                }
                if (--persistCountdown <= 0) {
                    pref.persistTimer(remainingMillis);
                    persistCountdown = PERSIST_EVERY_TICKS;
                }
                callback.onTick(remainingMillis);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(tickRunnable);
    }

    public void stop() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }

    public void registerScreenListener() {
        if (powerManager != null) {
            screenOn = powerManager.isInteractive();
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOn = true;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            context.registerReceiver(screenReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "registerScreenListener failed", e);
        }
    }

    public void unregisterScreenListener() {
        if (screenReceiver != null) {
            try {
                context.unregisterReceiver(screenReceiver);
            } catch (Exception e) {
            }
            screenReceiver = null;
        }
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    public boolean isScreenOn() {
        return screenOn;
    }

    static void scheduleExpiryAlarm(Context context, long remainingMillis) {
        if (remainingMillis <= 0) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(AlarmReceiver.ACTION_TIMER_CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long triggerAt = SystemClock.elapsedRealtime() + remainingMillis;
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            Log.i(TAG, "Expiry alarm scheduled in " + (remainingMillis / 1000) + "s");
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            Log.w(TAG, "Exact alarm denied, using inexact", e);
        }
    }
}
