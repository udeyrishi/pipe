package com.udeyrishi.pipe.barrier

import com.udeyrishi.pipe.util.immutableAfterSet

interface ManualBarrierController {
    fun lift()
}

internal class ManualBarrierControllerImpl<T : Any> : BarrierController<T>, ManualBarrierController {
    private val lock = Any()
    private val unliftedBarriers = mutableListOf<Barrier<T>>()
    private var lifted by immutableAfterSet(false)

    override fun onBarrierCreated(barrier: Barrier<T>) {
        synchronized(lock) {
            if (lifted) {
                barrier.lift()
            } else {
                unliftedBarriers.add(barrier)
            }
        }
    }

    override fun onBarrierBlocked(barrier: Barrier<T>) {
        synchronized(lock) {
            if (barrier !in unliftedBarriers) {
                throw IllegalArgumentException("Something went wrong. $barrier should have never been blocked.")
            }
        }
    }

    override fun lift() {
        synchronized(lock) {
            lifted = true
            unliftedBarriers.forEach {
                it.lift()
            }
            unliftedBarriers.clear()
        }
    }
}
