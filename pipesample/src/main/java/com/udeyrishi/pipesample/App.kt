package com.udeyrishi.pipesample

import android.app.Application
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository

class App : Application() {
    companion object {
        lateinit var INSTANCE: App
            private set
    }

    val jobsRepo: MutableRepository<Job<*>> = InMemoryRepository()

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    override fun onTerminate() {
        jobsRepo.apply {
            items.forEach { (job, _, _) -> job.interrupt() }
            clear()
            close()
        }
        super.onTerminate()
    }
}
