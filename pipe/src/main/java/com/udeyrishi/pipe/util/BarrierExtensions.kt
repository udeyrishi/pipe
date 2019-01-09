/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.app.Application
import androidx.annotation.RequiresPermission
import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.PipelineDispatcher
import com.udeyrishi.pipe.internal.util.createConnectivityMonitor
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.toStrictAndroidPipeDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Represents an operation that can be cancelled.
 */
interface Cancellable {
    fun cancel()
}

/**
 * A utility extension function that can be used to automatically lift a [ManualBarrierController] when
 * a certain [condition] is a met. The [condition] will be evaluated every [periodicityMillis] milliseconds
 * on a coroutine allowed by the [pollingDispatcher].
 */
fun ManualBarrierController.liftWhen(periodicityMillis: Long = 500L, pollingDispatcher: PipelineDispatcher = Dispatchers.IO.toStrictAndroidPipeDispatcher(), condition: suspend () -> Boolean): Cancellable {
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

/**
 * A utility extension function that be used to lift a barrier when internet is available. It can be
 * used in front of the steps that require internet access.
 */
@RequiresPermission(ACCESS_NETWORK_STATE)
fun ManualBarrierController.liftWhenHasInternet(app: Application) {
    val connectivityMonitor = app.createConnectivityMonitor()
    connectivityMonitor += {
        if (it) {
            connectivityMonitor.stop()
            connectivityMonitor.clearObservers()
            lift()
        }
    }
    connectivityMonitor.start()
}
