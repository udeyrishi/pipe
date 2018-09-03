package com.udeyrishi.pipe.steps

internal data class StepDescriptor<T : Any>(val name: String, val maxAttempts: Long, val step: Step<T>) {
    init {
        if (maxAttempts <= 0L) {
            throw IllegalArgumentException("maxAttempts must be > 0.")
        }
    }
}
