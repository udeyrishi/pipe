/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import android.os.Handler

interface UnexpectedExceptionHandler {
    val handler: Handler
    fun handleException(throwable: Throwable)
}
