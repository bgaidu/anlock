package com.kidslock.app

import android.app.Application

class KidsLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = this
        val pref = PrefManager(this)
        if (pref.isTimerActive() || pref.isLocked()) {
            Revival.schedule(this)
            Diag.i("App", "process start, timer=${pref.isTimerActive()} locked=${pref.isLocked()}")
        } else {
            Diag.i("App", "process start, idle")
        }
    }
}
