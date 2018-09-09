package com.udeyrishi.pipe.util

import android.os.Handler
import android.os.Looper

object ThrowOnMainThreadExceptionHandler : UnexpectedExceptionHandler {
    override val handler: Handler = Handler(Looper.getMainLooper())

    override fun handleException(throwable: Throwable) {
        throw throwable
    }
}
