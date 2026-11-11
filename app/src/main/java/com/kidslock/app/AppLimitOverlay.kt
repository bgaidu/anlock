package com.kidslock.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.kidslock.app.prefs.AppLimitPolicy

/**
 * 单应用额度用完时的轻量拦截层。
 *
 * 与整机锁屏不同：它只盖住当前应用，桌面、电话、学习类应用不受影响。
 * 家长可从右下角进入设置页（自带 PIN 门禁）加时或改额度。
 */
class AppLimitOverlay(
    private val context: Context,
    private val callback: Callback
) {
    companion object {
        private const val TAG = "AppLimit"
    }

    interface Callback {
        fun onGoHome()
        fun onOpenSettings(packageName: String)
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var shownPackage: String? = null

    @Suppress("DEPRECATION")
    fun show(state: AppLimitRuntime.State, label: String) {
        if (!canDrawOverlays()) {
            Log.d(TAG, "overlay permission missing, skip app limit overlay")
            return
        }
        try {
            val view = overlayView ?: AppLimitView(context).also { created ->
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                windowManager.addView(created, params)
                overlayView = created
                created.requestFocus()
            }
            shownPackage = state.packageName
            view.findViewById<TextView>(R.id.tvAppLimitTitle)?.text = "「$label」今天的时间用完了"
            val used = AppLimitPolicy.formatUsage(state.usedMs)
            val limit = AppLimitPolicy.label(state.limitMinutes)
            view.findViewById<TextView>(R.id.tvAppLimitDetail)?.text =
                if (state.limitMinutes == 0)
                    "该应用已被家长设为禁止使用。\n请联系家长调整限额。"
                else
                    "已使用 $used，限额 $limit。\n换一个应用，或者请家长加时。"
        } catch (e: Exception) {
            Log.e(TAG, "show app limit overlay failed", e)
            hide()
        }
    }

    fun hide() {
        shownPackage = null
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "remove app limit overlay failed", e)
            }
            overlayView = null
        }
    }

    fun isShowing(): Boolean = overlayView != null

    fun isShowingFor(packageName: String): Boolean =
        overlayView != null && shownPackage == packageName

    fun shownPackage(): String? = shownPackage

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private inner class AppLimitView(context: Context) : FrameLayout(context) {
        init {
            LayoutInflater.from(context).inflate(R.layout.overlay_app_limit, this, true)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            findViewById<Button>(R.id.btnAppLimitHome)?.apply {
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
                setOnClickListener { callback.onGoHome() }
            }
            findViewById<Button>(R.id.btnAppLimitSettings)?.setOnClickListener {
                shownPackage?.let { pkg -> callback.onOpenSettings(pkg) }
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
                callback.onGoHome()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
}
