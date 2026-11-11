package com.kidslock.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 护眼休息提醒：全屏遮罩 20 秒，提示孩子看远处。
 *
 * 不新开 Activity（避免打断正在播放的视频/游戏存档），用悬浮窗盖在最上层；
 * 期间不可点击、不可返回，20 秒后自动消失。未授予悬浮窗权限时静默跳过并记日志。
 */
object EyeRestOverlay {
    private const val TAG = "EyeRest"
    private const val SECONDS = 20

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var countdownView: TextView? = null
    private var remaining = 0

    private val tick = object : Runnable {
        override fun run() {
            remaining--
            if (remaining <= 0) {
                hide()
            } else {
                updateText()
                handler.postDelayed(this, 1000)
            }
        }
    }

    fun isShowing(): Boolean = overlayView != null

    @Suppress("DEPRECATION")
    fun show(context: Context): Boolean {
        if (overlayView != null) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Diag.w(TAG, "overlay permission missing, skip eye rest")
            return false
        }
        val wm = context.applicationContext
            .getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val view = buildView(context)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        return try {
            wm.addView(view, params)
            overlayView = view
            windowManager = wm
            remaining = SECONDS
            updateText()
            handler.postDelayed(tick, 1000)
            Diag.i(TAG, "eye rest shown for ${SECONDS}s")
            true
        } catch (e: Exception) {
            Log.w(TAG, "show failed", e)
            false
        }
    }

    fun hide() {
        handler.removeCallbacks(tick)
        val view = overlayView
        val wm = windowManager
        overlayView = null
        windowManager = null
        countdownView = null
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "remove failed", e)
            }
        }
    }

    private fun updateText() {
        countdownView?.text = "还剩 $remaining 秒"
    }

    private fun buildView(context: Context): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(235, 0, 0, 0))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 48), dp(context, 32), dp(context, 48), dp(context, 32))
        }
        column.addView(TextView(context).apply {
            text = "让眼睛休息一下"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            gravity = Gravity.CENTER
        })
        column.addView(TextView(context).apply {
            text = "看看窗外远处，放松一下"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, dp(context, 16))
        })
        val countdown = TextView(context).apply {
            setTextColor(Color.parseColor("#FFD54F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            text = "还剩 $SECONDS 秒"
        }
        countdownView = countdown
        column.addView(countdown)
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return root
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
