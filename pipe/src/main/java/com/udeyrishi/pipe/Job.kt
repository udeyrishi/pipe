/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import androidx.lifecycle.LiveData
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.pipeline.Passenger
import com.udeyrishi.pipe.util.Identifiable

class Job<T : Any> internal constructor(private val orchestrator: Orchestrator<Passenger<T>>) : Identifiable by orchestrator {
    val state: LiveData<State>
        get() = orchestrator.state

    val result: T?
        get() = orchestrator.result?.data

    fun interrupt(): Unit = orchestrator.interrupt()
}
