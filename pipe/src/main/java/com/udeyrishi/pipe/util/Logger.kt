/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

/**
 * An abstract logger that can be used for monitoring important events in the pipelines.
 *
 * Method names have the same naming conventions as [android.util.Log].
 */
interface Logger {
    /**
     * Logs any errors that may have occurred.
     *
     * @param message A description of the error.
     */
    fun e(message: String)

    /**
     * Logs any informational events.
     *
     * @param message The description of the event.
     */
    fun i(message: String)

    /**
     * Logs any detailed debug information.
     *
     * @param message The description of the event.
     */
    fun d(message: String)
}
