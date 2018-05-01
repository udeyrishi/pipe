/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

internal data class StepDescriptor<T>(val name: String, val maxAttempts: Long, val step: Step<T>)