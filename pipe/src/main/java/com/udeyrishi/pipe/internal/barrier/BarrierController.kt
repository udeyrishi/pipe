package com.udeyrishi.pipe.internal.barrier

internal interface BarrierController<T : Any> {
    fun onBarrierCreated(barrier: Barrier<T>)
    suspend fun onBarrierBlocked(barrier: Barrier<T>)
    fun onBarrierInterrupted(barrier: Barrier<T>)
}
