/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import androidx.lifecycle.LiveData
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.pipeline.Passenger
import com.udeyrishi.pipe.util.Identifiable

/**
 * Represents a chunk of work that is being processed in a pipeline. When an item gets
 * pushed into a pipeline, the pipeline returns a [Job] to track its progress.
 */
class Job<T : Any> internal constructor(private val orchestrator: Orchestrator<Passenger<T>>) : Identifiable by orchestrator {
    /**
     * A [LiveData] representing the state of the job.
     */
    val state: LiveData<State>
        get() = orchestrator.state

    /**
     * The final result of the job. Is set to a non-null value once the state reaches [com.udeyrishi.pipe.State.Terminal.Success] value.
     */
    val result: T?
        get() = orchestrator.result?.data

    /**
     * Tries to interrupt an ongoing job in a best-effort cancellation fashion.
     */
    fun interrupt(): Unit = orchestrator.interrupt()
}
