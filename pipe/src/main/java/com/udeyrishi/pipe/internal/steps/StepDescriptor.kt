/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.steps

import com.udeyrishi.pipe.Step

internal data class StepDescriptor<T : Any>(val name: String, val maxAttempts: Long, val step: InterruptibleStep<T>) {
    init {
        if (maxAttempts <= 0L) {
            throw IllegalArgumentException("maxAttempts must be > 0.")
        }
    }

    constructor(name: String, maxAttempts: Long, step: Step<T>) : this(name, maxAttempts, nonInterruptibleStep(step))
}
