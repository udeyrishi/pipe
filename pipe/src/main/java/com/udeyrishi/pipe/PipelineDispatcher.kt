/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

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

internal fun PipelineDispatcher.createEffectiveContext(): CoroutineContext {
    return coroutineDispatcher + CoroutineExceptionHandler { _, throwable ->
        onInternalPipeError(throwable)
    }
}
