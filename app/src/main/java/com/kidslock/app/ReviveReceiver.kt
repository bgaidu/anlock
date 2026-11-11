package com.kidslock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager 复活闹钟的接收端（未导出，只接受本应用的 PendingIntent）。 */
class ReviveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Revival.REVIVE_ACTION) return
        Revival.ensureRunning(context, "alarm")
    }
}
