/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.udeyrishi.pipe.internal.steps.InterruptibleStep
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal interface Barrier<T : Any> : InterruptibleStep<T> {
    val input: T?
    fun lift(result: T? = null)
    fun markAsFailed(e: Throwable)
}

internal class BarrierImpl<T : Any>(private val controller: BarrierController<T>) : Barrier<T> {
    private var continuation: Continuation<T?>? = null
    private val lock = Any()
    override var input: T? = null
        private set
    private var result: T? = null

    private var state: BarrierState = BarrierState.Initialized

    init {
        controller.onBarrierCreated(this)
    }

    override suspend operator fun invoke(input: T): T? {
        if (this.input != null) {
            throw IllegalStateException("A Barrier cannot be invoked 2x without an intermediate failure.")
        }
        this.input = input

        return state.let { capturedState1 ->
            when (capturedState1) {
                is BarrierState.Blocked -> throw IllegalStateException("A Barrier cannot be blocked 2x without an intermediate failure.")
                is BarrierState.ResultPrepared -> capturedState1.evaluateResult()
                is BarrierState.Initialized -> {
                    controller.onBarrierBlocked(this)
                    suspendCoroutine { _continuation ->
                        synchronized(lock) {
                            state.let { capturedState2 ->
                                when (capturedState2) {
                                    is BarrierState.Blocked -> throw IllegalStateException("A Barrier cannot be blocked 2x without an intermediate failure.")
                                    is BarrierState.ResultPrepared -> {
                                        try {
                                            _continuation.resume(capturedState2.evaluateResult())
                                        } catch (e: Throwable) {
                                            _continuation.resumeWithException(e)
                                        }
                                    }
                                    is BarrierState.Initialized -> {
                                        this.continuation = _continuation
                                        this.state = BarrierState.Blocked
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * lift() might get call asynchronously while the block inside suspendCoroutine is running.
         * Do the standard before-and-after check to ensure lift() wasn't called. If it was called, bail early.
         * Else, set the `continuation`. When lift() is indeed called, it will resume the coroutine via the continuation.
         *
         * `onBarrierBlocked` may cause the barrier to be lifted based on the controller implementation,
         * so this before and after check is essential.
         */
    }

    override fun lift(result: T?) {
        if (this.result != null) {
            throw IllegalStateException("Cannot set result 2x without a reset.")
        }
        this.result = result
        synchronized(lock) {
            state.let {
                when (it) {
                    BarrierState.Initialized, BarrierState.Blocked -> {
                        state = BarrierState.ResultPrepared.Lifted
                        try {
                            continuation?.resume(BarrierState.ResultPrepared.Lifted.evaluateResult())
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                        }
                    }
                    BarrierState.ResultPrepared.Lifted, BarrierState.ResultPrepared.Interrupted -> { /* no-op */ }
                    else -> throw IllegalStateException("Cannot prepare result when state is $it")
                }
            }
        }
    }

    override fun interrupt() {
        synchronized(lock) {
            state.let {
                when (it) {
                    BarrierState.Initialized, BarrierState.Blocked -> {
                        state = BarrierState.ResultPrepared.Interrupted
                        try {
                            controller.onBarrierInterrupted(this)
                            continuation?.resume(BarrierState.ResultPrepared.Interrupted.evaluateResult())
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                        }
                    }
                    BarrierState.ResultPrepared.Lifted, BarrierState.ResultPrepared.Interrupted -> { /* no-op */ }
                    else -> throw IllegalStateException("Cannot prepare result when state is $it")
                }
            }
        }
    }

    override fun markAsFailed(e: Throwable) {
        val resultState = BarrierState.ResultPrepared.Error(e)
        synchronized(lock) {
            state.let {
                when (it) {
                    BarrierState.Initialized, BarrierState.Blocked -> {
                        state = resultState
                        try {
                            continuation?.resume(resultState.evaluateResult())
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                        }
                    }
                    else -> throw IllegalStateException("Cannot prepare result when state is $it")
                }
            }
        }
    }

    private fun BarrierState.ResultPrepared.evaluateResult(): T? {
        return when (this) {
            is BarrierState.ResultPrepared.Lifted -> result ?: input ?: throw IllegalStateException("Something went wrong. Result must always be non-null after a barrier lift.")
            is BarrierState.ResultPrepared.Interrupted -> null
            is BarrierState.ResultPrepared.Error -> {
                reset()
                throw error
            }
        }
    }

    private fun reset() {
        this.state = BarrierState.Initialized
        this.input = null
        this.result = null
    }

    private sealed class BarrierState {
        object Initialized : BarrierState()
        object Blocked : BarrierState()

        sealed class ResultPrepared : BarrierState() {
            class Error(val error: Throwable) : ResultPrepared()
            object Interrupted : ResultPrepared()
            object Lifted : ResultPrepared()
        }
    }
}
