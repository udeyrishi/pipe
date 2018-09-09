package com.udeyrishi.pipe.barrier

internal interface BarrierController<T : Any> {
    fun onBarrierCreated(barrier: Barrier<T>)
    fun onBarrierBlocked(barrier: Barrier<T>)
}
