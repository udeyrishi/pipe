/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

import com.udeyrishi.pipe.util.immutableAfterSet
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class Barrier<T : Any> internal constructor() {
    private var lifted: Boolean by immutableAfterSet(false)
    private val continuations = mutableListOf<Pair<Continuation<T>, T>>()
    private val lock = Any()

    val blockedCount: Int
        get() = continuations.size

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    internal suspend fun blockUntilLift(input: T): T {
        if (lifted) {
            return input
        }

        /**
         * lift() might get call asynchronously while the block inside suspendCoroutine is running.
         * Do the standard before-and-after check to ensure lift() wasn't called. If it was called, bail early.
         * Else, set the `continuation`. When lift() is indeed called, it will resume the coroutine via the continuation.
         */
        return suspendCoroutine {
            synchronized(lock) {
                if (lifted) {
                    it.resume(input)
                } else {
                    continuations.add(it to input)
                }
            }
            // If it.resume(input) isn't called until this point, suspendCoroutine will block the current coroutine
            // until someone calls `resume` (see: lift() below).
        }
    }

    fun lift() {
        if (!lifted) {
            synchronized(lock) {
                lifted = true
                continuations.forEach { (continuation, input) ->
                    continuation.resume(input)
                }
                continuations.clear()
            }
        }
    }
}

