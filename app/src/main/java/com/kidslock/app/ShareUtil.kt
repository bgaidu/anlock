package com.kidslock.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 系统分享的公共入口：周报、诊断日志、家长操作记录都走这里。 */
object ShareUtil {

    fun shareText(context: Context, text: String, subject: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            val chooser = Intent.createChooser(intent, chooserTitle)
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
        }
    }
}
