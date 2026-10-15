package com.kidslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class LockService extends Service {

    private static final String TAG = "LockService";
    private static final String CHANNEL_ID = "kids_lock_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_UNLOCK = "com.kidslock.app.ACTION_UNLOCK";
    static final String ACTION_HOLD_OVERLAY = "com.kidslock.app.ACTION_HOLD_OVERLAY";

    static volatile LockService runningInstance;

    private PrefManager pref;
    private Handler handler;
    private TimerManager timerManager;
    private LockGuard lockGuard;
    private LockOverlayManager overlayManager;

    @Override
    public void onCreate() {
        super.onCreate();
        pref = new PrefManager(this);
        handler = new Handler(Looper.getMainLooper());
        timerManager = new TimerManager(this, pref, new TimerManager.TimerCallback() {
            @Override
            public void onTick(long remainingMillis) {
                updateNotification();
            }
            @Override
            public void onExpired() {
                triggerLock();
            }
        });
        lockGuard = new LockGuard(this, pref, new LockGuard.GuardCallback() {
            @Override
            public void onLockScreenPulled() {
                startLockScreenActivity();
            }
            @Override
            public void onWatchdogStopped() {
                overlayManager.hideOverlay();
                stopForeground(true);
                stopSelf();
            }
        });
        overlayManager = new LockOverlayManager(this, () -> startLockScreenActivity());
        timerManager.registerScreenListener();
        createNotificationChannel();
        runningInstance = this;
        Log.i(TAG, "LockService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification(pref.getRemainingMillis()));
        updateNotification();

        if (intent != null && ACTION_UNLOCK.equals(intent.getAction())) {
            unlockAndStop();
            return START_STICKY;
        }

        if (intent != null && ACTION_HOLD_OVERLAY.equals(intent.getAction())) {
            overlayManager.showOverlayIfPermitted();
            return START_STICKY;
        }

        if (pref.isLocked()) {
            Log.i(TAG, "Device locked, starting lock watchdog");
            lockGuard.start();
            overlayManager.showOverlayIfPermitted();
            return START_STICKY;
        }

        if (!pref.isTimerActive()) {
            Log.i(TAG, "Timer not active, stopping service");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (pref.isTimerExpired()) {
            Log.i(TAG, "Timer expired, locking now");
            triggerLock();
            return START_STICKY;
        }

        timerManager.start();
        return START_STICKY;
    }

    private void triggerLock() {
        Log.i(TAG, "Triggering lock screen!");
        pref.setLocked(true);
        pref.setHomeAliasEnabled(this, true);
        pref.stopTimer();
        updateNotification();
        overlayManager.showOverlayIfPermitted();
        startLockScreenActivity();
        lockGuard.start();
    }

    private void startLockScreenActivity() {
        handler.postDelayed(() -> {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(lockIntent);
            } catch (Exception e) {
                Log.e(TAG, "Start LockScreenActivity failed", e);
            }
        }, 300);
    }

    public static void requestUnlock(Context context) {
        Intent intent = new Intent(context, LockService.class);
        intent.setAction(ACTION_UNLOCK);
        context.startService(intent);
    }

    private void unlockAndStop() {
        pref.setLocked(false);
        pref.setHomeAliasEnabled(this, false);
        pref.resetPinFailures();
        overlayManager.hideOverlay();
        lockGuard.stop();
        timerManager.start();
    }

    public void showOverlayIfPermitted() {
        if (overlayManager != null) {
            overlayManager.showOverlayIfPermitted();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "儿童锁屏计时",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("观看时间计时通知");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(long remaining) {
        String contentText;
        if (pref.isLocked()) {
            contentText = "设备已锁定，答对汉字即可解锁";
        } else if (!timerManager.isScreenOn()) {
            contentText = "熄屏暂停中 · 剩余观看时间：" + formatTime(remaining);
        } else {
            contentText = "剩余观看时间：" + formatTime(remaining);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("儿童锁屏运行中")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(timerManager.getRemainingMillis()));
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        timerManager.stop();
        timerManager.unregisterScreenListener();
        lockGuard.stop();
        overlayManager.hideOverlay();
        if (runningInstance == this) runningInstance = null;
        Log.i(TAG, "LockService destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
