/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher

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
 * Converts a [CoroutineDispatcher] to a [PipelineDispatcher], such that any unhandled exceptions
 * are thrown on the Android UI thread.
 */
fun CoroutineDispatcher.toStrictAndroidPipeDispatcher(): PipelineDispatcher {
    return object : PipelineDispatcher {
        private val handler = Handler(Looper.getMainLooper())

        override val coroutineDispatcher = this@toStrictAndroidPipeDispatcher

        override fun onInternalPipeError(throwable: Throwable) {
            handler.post {
                throw throwable
            }
        }
    }
}
