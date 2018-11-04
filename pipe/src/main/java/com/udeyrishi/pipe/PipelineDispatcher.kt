/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface PipelineDispatcher {
    val coroutineDispatcher: CoroutineDispatcher

    // Will be invoked on a coroutine supplied by the `coroutineDispatcher`
    fun onInternalPipeError(throwable: Throwable)
}

object DefaultAndroidDispatcher : PipelineDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onInternalPipeError(throwable: Throwable) {
        handler.post {
            throw throwable
        }
    }
}
