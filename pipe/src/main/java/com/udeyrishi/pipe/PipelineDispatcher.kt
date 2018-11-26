/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.os.Handler
import android.os.Looper
import com.udeyrishi.pipe.DefaultAndroidDispatcher.onInternalPipeError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Represents a dispatcher that will be used to start all pipe coroutines.
 * This dispatcher is converted into an effective [kotlin.coroutines.CoroutineContext] by joining the
 * [coroutineDispatcher] and the [onInternalPipeError] implementation.
 */
interface PipelineDispatcher {
    /**
     * The dispatcher to be used for launching job coroutines.
     */
    val coroutineDispatcher: CoroutineDispatcher

    /**
     * A fallback handler that will be invoked if there are any unexpected exceptions in pipe.
     * This does not include actual logical errors that can be safely thrown in the different steps.
     * This is only for safely dealing with bugs in the pipe project.
     *
     * Will be invoked on a coroutine supplied by the [coroutineDispatcher]
     */
    fun onInternalPipeError(throwable: Throwable)
}

/**
 * A standard implementation of [PipelineDispatcher] that can be used by most apps.
 * Executes all the jobs on [Dispatchers.IO], and throws any internal errors on the Android UI thread.
 *
 * This is only meant as an example. In real production apps, you might want to replace [onInternalPipeError]
 * with an implementation that logs the exceptions to some error reporting service.
 */
object DefaultAndroidDispatcher : PipelineDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onInternalPipeError(throwable: Throwable) {
        handler.post {
            throw throwable
        }
    }
}
