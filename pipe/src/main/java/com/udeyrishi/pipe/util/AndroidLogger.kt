/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import android.util.Log

/**
 * A `Logger` implementation that forwards the logs to the standard android `android.util.Log` logger.
 */
class AndroidLogger(private val logTag: String) : Logger {
    override fun e(message: String) {
        Log.e(logTag, message)
    }

    override fun i(message: String) {
        Log.i(logTag, message)
    }

    override fun d(message: String) {
        Log.d(logTag, message)
    }
}
