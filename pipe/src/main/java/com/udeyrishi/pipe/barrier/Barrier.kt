/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.barrier

import com.udeyrishi.pipe.steps.InterruptibleStep
import com.udeyrishi.pipe.util.immutableAfterSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal interface Barrier<T : Any> : InterruptibleStep<T> {
    val input: T?
    fun lift(result: T? = null)
}

internal class BarrierImpl<T : Any>(private val controller: BarrierController<T>) : Barrier<T> {
    private var lifted by immutableAfterSet(false)
    private var continuation: Continuation<T?>? = null
    private val lock = Any()
    override var input: T? by immutableAfterSet(null)
        private set
    private var result: T? by immutableAfterSet(null)
    private var interrupted by immutableAfterSet(false)

    init {
        controller.onBarrierCreated(this)
    }

    override suspend operator fun invoke(input: T): T? {
        this.input = input

        if (lifted) {
            return getEvaluatedResult()
        }

        controller.onBarrierBlocked(this)

        /**
         * lift() might get call asynchronously while the block inside suspendCoroutine is running.
         * Do the standard before-and-after check to ensure lift() wasn't called. If it was called, bail early.
         * Else, set the `continuation`. When lift() is indeed called, it will resume the coroutine via the continuation.
         *
         * `onBarrierBlocked` may cause the barrier to be lifted based on the controller implementation,
         * so this before and after check is essential.
         */
        return suspendCoroutine {
            synchronized(lock) {
                if (lifted) {
                    it.resume(getEvaluatedResult())
                } else {
                    this.continuation = it
                }
            }
            // If it.resume(input) isn't called until this point, suspendCoroutine will block the current coroutine
            // until someone calls `resume` (see: lift() below).
        }
    }

    override fun lift(result: T?) {
        if (!lifted) {
            synchronized(lock) {
                markAsLifted(result)
            }
        }
    }

    override fun interrupt() {
        if (!interrupted) {
            synchronized(lock) {
                if (!lifted) {
                    this.interrupted = true
                    markAsLifted(null)
                    controller.onBarrierInterrupted(this)
                }
            }
        }
    }

    private fun markAsLifted(result: T?) {
        this.lifted = true
        this.result = result
        this.continuation?.resume(getEvaluatedResult())
        this.continuation = null
    }

    private fun getEvaluatedResult(): T? {
        return if (interrupted) {
            null
        } else {
            result ?: input ?: throw IllegalStateException("Something went wrong. Result must always be non-null after a barrier lift.")
        }
    }
}
