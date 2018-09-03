/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

typealias Step<T> = suspend (input: T) -> T

internal data class StepDescriptor<T : Any>(val name: String, val maxAttempts: Long, val step: Step<T>) {
    init {
        if (maxAttempts <= 0L) {
            throw IllegalArgumentException("maxAttempts must be > 0.")
        }
    }
}
