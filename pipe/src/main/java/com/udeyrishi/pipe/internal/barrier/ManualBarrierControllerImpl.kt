/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.internal.util.immutableAfterSet

internal class ManualBarrierControllerImpl<T : Any> : BarrierController<T>, ManualBarrierController {
    private val lock = Any()
    // Registered barrier to whether it's blocked or not
    private val unliftedBarriers = mutableMapOf<Barrier<T>, Boolean>()
    private var lifted by immutableAfterSet(false)

    override fun onBarrierCreated(barrier: Barrier<T>) {
        synchronized(lock) {
            when {
                lifted -> barrier.lift()
                unliftedBarriers.contains(barrier) -> throw IllegalArgumentException("Cannot register $barrier 2x.")
                else -> unliftedBarriers.put(barrier, false)
            }
        }
    }

    override fun onBarrierBlocked(barrier: Barrier<T>) {
        synchronized(lock) {
            if (barrier !in unliftedBarriers && !lifted) {
                // The barrier might be missing in barriers if the controller was lifted a moment ago. Possible
                // if the barrier.invoke was racing with it.

                // Safe to ignore this call then, since the barrier will check for the lift again momentarily.
                throw IllegalArgumentException("Something went wrong. $barrier should have never been blocked.")
            }

            if (unliftedBarriers[barrier] == true) {
                throw IllegalArgumentException("Cannot mark $barrier blocked 2x.")
            }

            unliftedBarriers[barrier] = true
        }
    }

    override fun lift() {
        synchronized(lock) {
            if (!lifted) {
                lifted = true
                unliftedBarriers.forEach { (barrier, _) ->
                    barrier.lift()
                }
                unliftedBarriers.clear()
            }
        }
    }

    override fun onBarrierInterrupted(barrier: Barrier<T>) {
        synchronized(lock) {
            unliftedBarriers.remove(barrier)
                    ?: throw IllegalArgumentException("Something went wrong. $barrier should have never been blocked.")
        }
    }
}
