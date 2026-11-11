package com.kidslock.app

import android.app.job.JobParameters
import android.app.job.JobService

/** 系统持久化定时任务的落地端：只负责把服务拉回来，不做业务判断。 */
class WatchdogJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Revival.ensureRunning(this, "job")
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
