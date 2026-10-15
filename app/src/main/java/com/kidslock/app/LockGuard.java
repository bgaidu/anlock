package com.kidslock.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public final class LockGuard {
    private static final String TAG = "LockService";
    private static final long GUARDIAN_CHECK_INTERVAL_MS = 15_000;

    public interface GuardCallback {
        void onLockScreenPulled();
        void onWatchdogStopped();
    }

    private final Context context;
    private final PrefManager pref;
    private final Handler handler;
    private final GuardCallback callback;

    private Runnable watchdogRunnable;
    private long lastGuardianCheckElapsed;

    public LockGuard(Context context, PrefManager pref, GuardCallback callback) {
        this.context = context.getApplicationContext();
        this.pref = pref;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
        }
        watchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pref.isLocked()) {
                    Log.i(TAG, "Unlocked, watchdog stopping");
                    callback.onWatchdogStopped();
                    return;
                }
                ensureAccessibilityGuardian();
                if (LockScreenActivity.isOnTop()) {
                    handler.postDelayed(this, 800);
                    return;
                }
                if (!LockScreenActivity.isStarting()) {
                    Log.i(TAG, "Lock screen not on top, trying to pull");
                    callback.onLockScreenPulled();
                }
                handler.postDelayed(this, 800);
            }
        };
        handler.post(watchdogRunnable);
    }

    public void stop() {
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
            watchdogRunnable = null;
        }
    }

    private void ensureAccessibilityGuardian() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastGuardianCheckElapsed < GUARDIAN_CHECK_INTERVAL_MS) return;
        lastGuardianCheckElapsed = now;
        if (!RecoveryAccessibilityService.isServiceEnabled(context)) {
            Log.w(TAG, "Accessibility guardian missing from settings, self-enabling");
            RecoveryAccessibilityService.trySelfEnable(context);
        }
    }
}
