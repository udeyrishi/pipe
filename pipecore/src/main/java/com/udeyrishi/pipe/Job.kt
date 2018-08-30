/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.state.State
import com.udeyrishi.pipe.state.StateChangeListener
import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

class Job<T : Any> internal constructor(private val orchestrator: Orchestrator<Pipeline.Passenger<T>>) : Identifiable {
    override val uuid: UUID
        get() = orchestrator.uuid

    val state: State
        get() = orchestrator.state

    val result: T?
        get() = orchestrator.result?.data

    fun start() {
        orchestrator.start()
    }

    fun interrupt() {
        orchestrator.interrupt()
    }

    fun subscribe(stateChangeListener: StateChangeListener) = orchestrator.subscribe(stateChangeListener)
    fun unsubscribe(stateChangeListener: StateChangeListener) = orchestrator.unsubscribe(stateChangeListener)
}
