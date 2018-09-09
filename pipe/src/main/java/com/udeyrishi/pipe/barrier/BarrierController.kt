package com.udeyrishi.pipe.barrier

internal interface BarrierController<T : Any> {
    fun onBarrierCreated(barrier: Barrier<T>)
    suspend fun onBarrierBlocked(barrier: Barrier<T>)
}
