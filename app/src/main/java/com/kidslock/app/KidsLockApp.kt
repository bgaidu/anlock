package com.kidslock.app

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.os.Process

class KidsLockApp : Application() {
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) = ForegroundUi.onActivityStarted()
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) = ForegroundUi.onActivityStopped()
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        AppContextHolder.context = this
        val pref = PrefManager(this)
        if (pref.isTimerActive() || pref.isLocked() || pref.isAppLimitEnabled()) {
            Revival.schedule(this)
            Diag.i(
                "App",
                "process start, timer=${pref.isTimerActive()} locked=${pref.isLocked()} " +
                        "appLimit=${pref.isAppLimitEnabled()}"
            )
        } else {
            Diag.i("App", "process start, idle")
        }
    }

    /** 未捕获异常先落盘再交给系统，家长能在诊断日志里看到崩溃原因。 */
    private fun installCrashLogger() {
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                Diag.e("Crash", "uncaught in ${thread.name}", error)
            } catch (_: Throwable) {
            }
            val previous = previousCrashHandler
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
