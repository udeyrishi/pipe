/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.barrier

import com.udeyrishi.pipe.steps.Step
import com.udeyrishi.pipe.util.SortReplayer
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlin.coroutines.experimental.CoroutineContext

interface CountedBarrierController {
    val arrivalCount: Int
    fun getCapacity(): Int
    fun setCapacity(capacity: Int)
}

internal class CountedBarrierControllerImpl<T : Comparable<T>>(private val launchContext: CoroutineContext = Dispatchers.Default, private var capacity: Int = Int.MAX_VALUE, private val onBarrierLiftedAction: Step<List<T>>? = null) : BarrierController<T>, CountedBarrierController {
    private val lock = Any()

    override var arrivalCount: Int = 0
        private set(value) {
            if (value > registeredCount) {
                throw IllegalStateException("Something went wrong. arrivalCount must never exceed the registeredCount.")
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

    // Registered barrier to whether it's blocked or not
    private val barriers = mutableMapOf<Barrier<T>, Boolean>()
    private var interrupted = false
        set(value) {
            if (field && !value) {
                throw IllegalArgumentException("interrupted cannot go from true to false.")
            }
            field = value
        }

    private var shouldExpectAbsentees: Boolean = false

    override fun getCapacity(): Int = capacity

    fun changeCapacityDueToError(capacity: Int) {
        synchronized(lock) {
            if (registeredCount > capacity) {
                // safe to abandon these registrations, since they have failed.
                registeredCount = capacity
                shouldExpectAbsentees = true
            }
            setCapacity(capacity)
        }
    }

    override fun setCapacity(capacity: Int) {
        synchronized(lock) {
            if (registeredCount > capacity) {
                throw IllegalStateException("Cannot change the capacity from ${this.capacity} to $capacity, because $registeredCount items have already been registered.")
            }
            this.capacity = capacity
            if (arrivalCount == capacity) {
                // We now need to retroactively mark the last arrived input as the final input. Usually, the last arrival's coroutine can be reused for aggregation purposes, but it's blocked
                // on the barrier now. So temporarily create a new one to do the aggregation + unblocking work.
                GlobalScope.launch(launchContext) {
                    onFinalInputPushed()
                }
            }
        }
    }

    override fun onBarrierCreated(barrier: Barrier<T>) {
        synchronized(lock) {
            if (barriers.contains(barrier)) {
                throw IllegalArgumentException("Cannot register $barrier 2x.")
            }
            if (interrupted) {
                barrier.interrupt()
            } else {
                ++registeredCount
                barriers.put(barrier, false)
            }
        }
    }

    override suspend fun onBarrierBlocked(barrier: Barrier<T>) {
        synchronized(lock) {
            when (barriers[barrier]) {
                null -> {
                    if (!interrupted) {
                        // barriers can be missing an interruption call races with a barrier getting blocked. Safely ignore this message,
                        // because the barrier was notified of the interruption too.
                        throw IllegalArgumentException("Barrier $barrier was never registered.")
                    }
                }
                true -> throw IllegalArgumentException("Barrier $barrier was already marked as blocked.")
                false -> {
                    barriers[barrier] = true
                    ++arrivalCount
                    if (arrivalCount == capacity) {
                        onFinalInputPushed()
                    }
                }
            }
        }
    }

    override fun onBarrierInterrupted(barrier: Barrier<T>) {
        synchronized(lock) {
            when {
                barrier in barriers -> {
                    interrupted = true
                    barriers.remove(barrier)
                    barriers.keys.forEach {
                        it.interrupt()
                    }
                    barriers.clear()
                }

                !interrupted -> {
                    // barrier can be missing in barriers in the case where a barrier.interrupt call leads to a controller.onBarrierInterrupted
                    throw IllegalArgumentException("Barrier $barrier was never registered.")
                }
            }
        }
    }

    private suspend fun onFinalInputPushed() {
        val blockedBarriers = barriers.filter { (_, blocked) ->
            blocked
        }.map { (barrier, _) ->
            barrier
        }

        val absenteeCount = barriers.size - blockedBarriers.size

        if (absenteeCount > 0 && !shouldExpectAbsentees) {
            throw IllegalStateException("All registered barriers must have been blocked by now. Something went wrong.")
        }

        val unsortedInputs = blockedBarriers.map {
            it.input ?: throw IllegalStateException("Barrier.input should've been generated by the time onBarrierBlocked was called.")
        }

        val results = if (onBarrierLiftedAction == null) {
            unsortedInputs
        } else {
            val sortedInputs = unsortedInputs.sorted()
            val sortReplayer = SortReplayer(original = unsortedInputs, sorted = sortedInputs)

            val sortedOutputs = onBarrierLiftedAction.invoke(sortedInputs.map { it })
            val unsortedOutputs = sortReplayer.reverseApplySortTransformations(sortedOutputs)
            unsortedOutputs
        }

        blockedBarriers.zip(results).forEach { (barrier, result) ->
            barrier.lift(result)
        }
        barriers.clear()
    }
}
