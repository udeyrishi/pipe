/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.barrier

import com.udeyrishi.pipe.steps.Step
import com.udeyrishi.pipe.util.SortReplayer
import kotlinx.coroutines.experimental.launch

interface CountedBarrierController {
    val arrivalCount: Int
    var capacity: Int
}

internal class CountedBarrierControllerImpl<T : Comparable<T>>(capacity: Int = Int.MAX_VALUE, private val onBarrierLiftedAction: Step<List<T>>? = null) : BarrierController<T>, CountedBarrierController {
    override var capacity: Int = capacity
        set(value) {
            synchronized(lock) {
                if (arrivalCount > value) {
                    throw IllegalStateException("Cannot change the capacity from $capacity to $value, because $arrivalCount items have already arrived.")
                }
                field = value
                if (arrivalCount == value) {
                    // We now need to retroactively mark the last arrived input as the final input. Usually, the last arrival's coroutine can be reused for aggregation purposes, but it's blocked
                    // on the barrier now. So temporarily create a new one to do the aggregation + unblocking work.
                    launch {
                        onFinalInputPushed()
                    }
                }
            }
        }

    private val lock = Any()
    override var arrivalCount: Int = 0
        private set(value) {
            if (value > capacity) {
                throw IllegalStateException("Something went wrong. ${this::class.java.simpleName} has reached its maximum capacity of $capacity, but another barrier was marked as blocked.")
            }
            field = value
        }

    private var registeredCount: Int = 0
        set(value) {
            if (value > capacity) {
                throw IllegalStateException("Something went wrong. ${this::class.java.simpleName} has reached its maximum capacity of $capacity, but another barrier was registered.")
            }
            field = value
        }

    private val barriers = mutableListOf<Barrier<T>>()

    override fun onBarrierCreated(barrier: Barrier<T>) {
        synchronized(lock) {
            ++registeredCount
            barriers.add(barrier)
        }
    }

    override fun onBarrierBlocked(barrier: Barrier<T>) {
        synchronized(lock) {
            if (barrier !in barriers) {
                throw IllegalArgumentException("Barrier $barrier was never registered.")
            }
            ++arrivalCount
            if (arrivalCount == capacity) {
                launch {
                    onFinalInputPushed()
                }
            }
        }
    }

    private suspend fun onFinalInputPushed() {
        if (onBarrierLiftedAction != null) {
            val unsortedInputs = barriers.map {
                it.input ?: throw IllegalStateException("Barrier.input should've been generated by the time onBarrierBlocked was called.")
            }

            val sortedInputs = unsortedInputs.sorted()
            val sortedOutputs = onBarrierLiftedAction.invoke(sortedInputs.map { it })
            val sortReplayer = SortReplayer(original = unsortedInputs, sorted = sortedInputs)
            val unsortedOutputs = sortReplayer.reverseApplySortTransformations(sortedOutputs)

            barriers.zip(unsortedOutputs).forEach { (barrier, result) ->
                barrier.lift(result)
            }

            barriers.clear()
        }
    }
}
