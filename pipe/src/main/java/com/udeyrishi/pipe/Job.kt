/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import androidx.lifecycle.LiveData
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.pipeline.Passenger
import com.udeyrishi.pipe.internal.util.Identifiable
import com.udeyrishi.pipe.internal.util.Logger
import java.util.UUID

class Job<T : Any> internal constructor(private val orchestrator: Orchestrator<Passenger<T>>) : Identifiable {
    override val uuid: UUID
        get() = orchestrator.uuid

    val state: LiveData<State>
        get() = orchestrator.state

    val result: T?
        get() = orchestrator.result?.data

    var logger: Logger?
        get() = orchestrator.logger
        set(value) {
            orchestrator.logger = value
        }

    fun interrupt(): Unit = orchestrator.interrupt()
}
