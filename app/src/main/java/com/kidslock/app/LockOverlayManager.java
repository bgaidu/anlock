package com.kidslock.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

public final class LockOverlayManager {
    private static final String TAG = "LockService";
    private static volatile long overlayHoldUntil;

    public interface OverlayCallback {
        void onLockScreenPulled();
    }

    private final Context context;
    private final WindowManager windowManager;
    private final OverlayCallback callback;

    private View overlayView;

    public LockOverlayManager(Context context, OverlayCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static void holdOverlay(Context context) {
        overlayHoldUntil = SystemClock.elapsedRealtime() + 5000;
        LockService s = LockService.runningInstance;
        if (s != null) {
            s.showOverlayIfPermitted();
            return;
        }
        Intent i = new Intent(context, LockService.class).setAction(LockService.ACTION_HOLD_OVERLAY);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (Exception e) {
            Log.e(TAG, "holdOverlay start service failed", e);
        }
    }

    public static long getOverlayHoldUntil() {
        return overlayHoldUntil;
    }

    public void showOverlayIfPermitted() {
        if (!canDrawOverlays()) {
            Log.d(TAG, "Overlay permission not granted, skip overlay");
            return;
        }
        if (overlayView != null) {
            return;
        }
        try {
            overlayView = new LockOverlayView(context, callback);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            windowManager.addView(overlayView, params);
            Log.i(TAG, "Overlay shown");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay", e);
        }
    }

    public void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove overlay", e);
            }
            overlayView = null;
        }
    }

    public boolean isOverlayShown() {
        return overlayView != null;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    private static final class LockOverlayView extends FrameLayout {
        public LockOverlayView(Context context, OverlayCallback callback) {
            super(context);
            LayoutInflater.from(context).inflate(R.layout.overlay_lock, this, true);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
            Button btnUnlock = findViewById(R.id.btnOverlayUnlock);
            if (btnUnlock != null) {
                btnUnlock.setFocusable(true);
                btnUnlock.setFocusableInTouchMode(true);
                btnUnlock.requestFocus();
                btnUnlock.setOnClickListener(v -> callback.onLockScreenPulled());
            }
            setOnClickListener(v -> callback.onLockScreenPulled());
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            for (event.getAction() == KeyEvent.ACTION_UP) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_SPACE) {
                    callback.onLockScreenPulled();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
    }
}
