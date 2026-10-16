package com.kidslock.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout

class LockOverlayManager(
    private val context: Context,
    private val callback: OverlayCallback
) {
    companion object {
        private const val TAG = "LockService"
        @Volatile
        private var overlayHoldUntil: Long = 0

        @JvmStatic
        fun holdOverlay(context: Context) {
            overlayHoldUntil = SystemClock.elapsedRealtime() + 5000
            val s = LockService.runningInstance
            if (s != null) {
                s.showOverlayIfPermitted()
                return
            }
            val i = Intent(context, LockService::class.java).setAction(LockService.ACTION_HOLD_OVERLAY)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                Log.e(TAG, "holdOverlay start service failed", e)
            }
        }

        @JvmStatic
        fun getOverlayHoldUntil(): Long = overlayHoldUntil
    }

    interface OverlayCallback {
        fun onLockScreenPulled()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showOverlayIfPermitted() {
        if (!canDrawOverlays()) {
            Log.d(TAG, "Overlay permission not granted, skip overlay")
            return
        }
        if (overlayView != null) return
        try {
            overlayView = LockOverlayView(context, callback)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager.addView(overlayView, params)
            Log.i(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay", e)
            }
            overlayView = null
        }
    }

    fun isOverlayShown(): Boolean = overlayView != null

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private class LockOverlayView(
        context: Context,
        private val callback: OverlayCallback
    ) : FrameLayout(context) {
        init {
            LayoutInflater.from(context).inflate(R.layout.overlay_lock, this, true)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            val btnUnlock = findViewById<Button>(R.id.btnOverlayUnlock)
            btnUnlock?.isFocusable = true
            btnUnlock?.isFocusableInTouchMode = true
            btnUnlock?.requestFocus()
            btnUnlock?.setOnClickListener { callback.onLockScreenPulled() }
            setOnClickListener { callback.onLockScreenPulled() }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_UP) {
                val keyCode = event.keyCode
                if (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_SPACE
                ) {
                    callback.onLockScreenPulled()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
    }
}
