/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.steps

import com.udeyrishi.pipe.Step

/**
 * We are intentionally publicly exposing a function type, while internally using an interface in
 * the orchestrator. This is because we want to deter people from using state in their steps.
 * They should be plain input-output functions.
 *
 * All user-supplied steps are non-interruptible (created through `nonInterruptibleStep`).
 */
internal interface InterruptibleStep<T : Any> {
    /**
     * Returns null iff the step was interrupted, and the step was able to successfully
     * respect the interruption request.
     */
    suspend operator fun invoke(input: T): T?

    /**
     * Attempts to interrupt the step. May or may not succeed at doing so. Check the result of `invoke`
     * to verify.
     */
    fun interrupt()
}

/**
 * Converts the function type `Step` into an `InterruptibleStep` that does not really support
 * interruption.
 */
internal fun <T : Any> nonInterruptibleStep(step: Step<T>): InterruptibleStep<T> {
    return object : InterruptibleStep<T> {
        override suspend fun invoke(input: T): T = step(input)
        override fun interrupt() = Unit
    }
}
