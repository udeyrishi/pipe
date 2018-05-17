/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.util.immutableAfterSet
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class Barrier<T : Any> internal constructor() {
    private var lifted: Boolean by immutableAfterSet(false)
    private var continuation: Continuation<T>? by immutableAfterSet(null)
    private var input: T? by immutableAfterSet(null)

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    internal suspend fun blockUntilLift(input: T): T {
        this.input = input
        if (lifted) {
            return input
        }

        /**
         * lift() might get call asynchronously while the block inside suspendCoroutine is running.
         * Do the standard before-and-after check to ensure lift() wasn't called. If it was called, bail early.
         * Else, set the `continuation`. When lift() is indeed called, it will resume the coroutine via the continuation.
         */
        return suspendCoroutine {
            if (lifted) {
                it.resume(input)
            } else {
                this.continuation = it
                if (lifted) {
                    it.resume(input)
                }
            }

            // If it.resume(input) isn't called until this point, suspendCoroutine will block the current coroutine
            // until someone calls `resume` (see: lift() below).
        }
    }

    fun lift() {
        if (!lifted) {
            lifted = true
            continuation?.resume(input ?: throw IllegalStateException("Something went wrong. continuation was initialized, but not the input."))
        }
    }
}

