package com.kidslock.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * 屏幕右上角的剩余时间角标。
 *
 * 设计要点：
 * - 不可聚焦、不可触摸，绝不抢遥控器焦点（电视上尤其重要）；
 * - 只在「计时中 + 未锁屏 + 屏幕亮着 + 应用设置页不在前台」时显示；
 * - 文字只在秒数变化时更新，避免每秒重设布局；
 * - 任何异常都只记日志，不影响计时与锁屏主流程。
 */
class CountdownOverlay(private val context: Context) {

    companion object {
        private const val TAG = "CountdownOverlay"
        private const val MARGIN_DP = 24
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var view: View? = null
    private var textView: TextView? = null
    private var lastText: String? = null

    fun show(remainingMillis: Long) {
        if (windowManager == null || !canDrawOverlays()) {
            hide()
            return
        }
        try {
            if (view == null && !attach()) return
            val label = format(remainingMillis)
            if (label != lastText) {
                textView?.text = label
                lastText = label
            }
            view?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.w(TAG, "show failed", e)
            hide()
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        textView = null
        lastText = null
        try {
            windowManager?.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "remove failed", e)
        }
    }

    fun isShowing(): Boolean = view != null

    fun destroy() = hide()

    private fun attach(): Boolean {
        val manager = windowManager ?: return false
        val root = LayoutInflater.from(context).inflate(R.layout.overlay_countdown, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            val margin = (MARGIN_DP * context.resources.displayMetrics.density).toInt()
            x = margin
            y = margin
        }
        manager.addView(root, params)
        view = root
        textView = root.findViewById(R.id.tvCountdown)
        return true
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun format(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("剩余 %d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("剩余 %02d:%02d", minutes, seconds)
        }
    }
}
