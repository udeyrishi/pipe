/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
typealias Step<T> = suspend (input: T) -> T

internal data class StepDescriptor<T : Any>(val name: String, val maxAttempts: Long, val step: Step<T>)