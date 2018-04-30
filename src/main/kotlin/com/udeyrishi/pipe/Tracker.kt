/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import java.io.Serializable
import java.util.UUID

class Tracker<T : Serializable> internal constructor(override val uuid: UUID, override val position: Long, private val steps: Iterator<StepDescriptor<T>>)
    : Serializable, Identifiable, Sequential, Comparable<Tracker<T>> {

    private var started = false
    private val stateChangeListeners = mutableListOf<StateChangeListener>()

    val isDone: Boolean
        get() = state is State.Terminal

    val hasSucceeded: Boolean
        get() = state is State.Terminal.Success

    val hasFailed: Boolean
        get() = state is State.Terminal.Failure

    val isRunning: Boolean
        get() = state is State.Running

    val isWaiting: Boolean
        get() = state is State.Scheduled

    var state: State = State.Scheduled()
        private set

    var result: T? = null
        private set

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    suspend fun start(vararg stateChangeListeners: StateChangeListener): T {
        if (started) {
            throw IllegalStateException("Cannot start a ${this.javaClass.simpleName} twice.")
        }

        started = true
        this.stateChangeListeners.addAll(stateChangeListeners)

        TODO("Navigate through the pipeline")
    }

    fun cancel() {
        TODO()
    }

    fun pause() {
        TODO()
    }

    fun resume() {
        TODO()
    }

    fun subscribe(stateChangeListener: StateChangeListener) {
        stateChangeListeners.add(stateChangeListener)
    }

    fun unsubscribe(stateChangeListener: StateChangeListener): Boolean {
        return stateChangeListeners.remove(stateChangeListener)
    }

    fun unsubscribeAll() {
        stateChangeListeners.clear()
    }

    override fun compareTo(other: Tracker<T>): Int = position.compareTo(other.position)
}