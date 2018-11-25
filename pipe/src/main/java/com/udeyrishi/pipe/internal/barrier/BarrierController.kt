/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

internal interface BarrierController<T : Any> {
    fun onBarrierCreated(barrier: Barrier<T>)
    fun onBarrierBlocked(barrier: Barrier<T>)
    fun onBarrierInterrupted(barrier: Barrier<T>)
}
