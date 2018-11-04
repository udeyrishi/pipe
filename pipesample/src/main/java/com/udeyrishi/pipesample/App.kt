package com.udeyrishi.pipesample

import android.app.Application
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.internal.util.AndroidLogger
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository

class App : Application() {
    companion object {
        lateinit var INSTANCE: App
            private set
    }

    val jobsRepo: MutableRepository<Job<ImagePipelineMember>> = InMemoryRepository()
    val logger = AndroidLogger("Pipe")

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    override fun onTerminate() {
        jobsRepo
                .getMatching { true }
                .map { it.identifiableObject }
                .forEach { it.interrupt() }

        jobsRepo.removeIf { true }

        super.onTerminate()
    }
}
