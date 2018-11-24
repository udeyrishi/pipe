/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

/**
 * An abstract logger that can be used for monitoring important events in the pipelines.
 *
 * Method names have the same naming conventions as `android.util.Log`.
 */
interface Logger {
    fun e(message: String)
    fun i(message: String)
    fun d(message: String)
}
