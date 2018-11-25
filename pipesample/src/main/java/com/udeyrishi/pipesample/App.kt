package com.udeyrishi.pipesample

import android.app.Application
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.util.AndroidLogger

class App : Application() {
    companion object {
        val jobsRepo: MutableRepository<Job<ImagePipelineMember>> = InMemoryRepository()
        val logger = AndroidLogger("Pipe")
    }

    override fun onTerminate() {
        jobsRepo.apply {
            items
                .map { it.identifiableObject }
                .forEach { it.interrupt() }

            clear()
            close()
        }
        super.onTerminate()
    }
}
