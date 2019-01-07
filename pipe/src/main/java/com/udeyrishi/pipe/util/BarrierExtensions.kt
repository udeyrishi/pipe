/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import com.udeyrishi.pipe.DefaultAndroidDispatcher
import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.PipelineDispatcher
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface Cancellable {
    fun cancel()
}

fun ManualBarrierController.liftWhen(periodicityMillis: Long = 500L, pollingDispatcher: PipelineDispatcher = DefaultAndroidDispatcher, condition: suspend () -> Boolean): Cancellable {
    val job = GlobalScope.launch(pollingDispatcher.createEffectiveContext()) {
        while (!condition() && isActive) {
            delay(periodicityMillis)
        }

        if (isActive) {
            lift()
        }
    }

    return object : Cancellable {
        override fun cancel() = job.cancel()
    }
}
