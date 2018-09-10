package com.udeyrishi.pipe.steps

internal data class StepDescriptor<T : Any>(val name: String, val maxAttempts: Long, val step: InterruptibleStep<T>) {
    init {
        if (maxAttempts <= 0L) {
            throw IllegalArgumentException("maxAttempts must be > 0.")
        }
    }

    constructor(name: String, maxAttempts: Long, step: Step<T>) : this(name, maxAttempts, nonInterruptibleStep(step))
}
