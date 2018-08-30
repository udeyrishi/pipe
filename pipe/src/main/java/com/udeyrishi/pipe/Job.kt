/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.arch.lifecycle.LiveData
import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

class Job<T : Any> internal constructor(private val orchestrator: Orchestrator<Pipeline.Passenger<T>>) : Identifiable {
    override val uuid: UUID
        get() = orchestrator.uuid

    val state: LiveData<State>
        get() = orchestrator.state

    val result: T?
        get() = orchestrator.result?.data

    fun start() {
        orchestrator.start()
    }

    fun interrupt() {
        orchestrator.interrupt()
    }
}
