package com.udeyrishi.pipe.internal.util

import android.util.Log

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
