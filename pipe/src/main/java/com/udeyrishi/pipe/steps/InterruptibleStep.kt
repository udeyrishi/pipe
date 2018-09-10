/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

/**
 * We are intentionally publicly exposing a function type, while internally using an interface in
 * the orchestrator. This is because we want to deter people from using state in their steps.
 * They should be plain input-output functions.
 */
internal interface InterruptibleStep<T : Any> {
    /**
     * Returns null iff the step was interrupted, and the step was able to successfully
     * respect the interruption request.
     */
    suspend operator fun invoke(input: T): T

    /**
     * Returns true iff the step is interruptible. Else, returns false.
     *
     * Returning true does not guarantee that the interruption will be successful. Check the result of
     * `invoke` to verify that
     */
    fun interrupt(): Boolean
}

/**
 * Converts the function type `Step` into an `InterruptibleStep` that does not really support
 * interruption.
 */
internal fun <T : Any> interruptibleStep(step: Step<T>): InterruptibleStep<T> {
    return object : InterruptibleStep<T> {
        override suspend fun invoke(input: T) = step(input)
        override fun interrupt() = false
    }
}
