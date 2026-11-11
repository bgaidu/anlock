package com.kidslock.app

import java.util.concurrent.atomic.AtomicInteger

/**
 * 本应用自己的 Activity 是否在前台。
 *
 * 应用限额的拦截层是 WindowManager 悬浮窗，不会触发 Activity 生命周期；用它来区分
 * 「孩子打开了被限额的应用」与「家长正在本应用设置页/权限页/锁屏页」两种情况，
 * 避免悬浮层盖住自己的界面，也避免把自己界面的前台事件当成孩子切回了应用。
 */
object ForegroundUi {
    private val startedActivities = AtomicInteger(0)

    val isVisible: Boolean
        get() = startedActivities.get() > 0

    fun onActivityStarted() {
        startedActivities.incrementAndGet()
    }

    fun onActivityStopped() {
        if (startedActivities.get() > 0) startedActivities.decrementAndGet()
    }
}
