/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

internal fun <T: Any, R> T.synchronizedRun(action: (T.() -> R)): R {
    return synchronized(this) {
        action()
    }
}