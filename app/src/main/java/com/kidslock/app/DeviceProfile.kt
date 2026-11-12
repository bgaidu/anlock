package com.kidslock.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * 设备形态判定：电视/盒子靠遥控器操作，平板/手机有触摸屏。
 *
 * 两端的答题交互差别很大（电视只能四向 + 确认，触屏可以直接打字），
 * 所以「是否提供填空题」这类决定交给这里统一判断，而不是在各处写死机型。
 *
 * 判据取「或」：个别国产电视不声明 leanback、也有盒子 uiMode 是 normal，
 * 用「没有触摸屏就按电视处理」兜底最稳。
 */
object DeviceProfile {

    /** 电视 / 盒子 / 投影：没有触摸屏，或系统自报为电视形态。 */
    fun isTelevision(context: Context): Boolean {
        if (!hasTouchscreen(context)) return true
        val pm = context.packageManager
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television")
    }

    fun hasTouchscreen(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    /** 写进诊断日志，远程排障时直接看这一行就够了。 */
    fun describe(context: Context): String =
        "tv=${isTelevision(context)} touch=${hasTouchscreen(context)}"
}
