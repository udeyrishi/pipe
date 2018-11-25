/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.udeyrishi.pipe.CountedBarrierController
import com.udeyrishi.pipe.Step
import com.udeyrishi.pipe.internal.util.SortReplayer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * The launchContext is only needed if this controller deems necessary to create a new coroutine. Usually, it'll be able to reuse existing acting coroutines.
 * However, the user may update the capacity retroactively via `setCapacity`. If the new capacity == the number of arrivals at that point, we'll need to unblock the barrier.
 * This unblocking action may need to perform a `onBarrierLiftedAction` suspending action. Therefore, a new coroutine would be needed.
 *
 * Once that action is performed, the pipelines would be resumed on their original coroutines.
 *
 * TL;DR: ensure that the `launchContext` is the same as the one you used for creating the pipeline.
 */
internal class CountedBarrierControllerImpl<T : Comparable<T>>(private val launchContext: CoroutineContext, private var capacity: Int = Int.MAX_VALUE, private val onBarrierLiftedAction: Step<List<T>>? = null) : BarrierController<T>, CountedBarrierController {
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

    private var expectedAbsenteeCount = 0

    private val isReadyToLift: Boolean
        get() = (arrivalCount + expectedAbsenteeCount) == capacity

    override fun getCapacity(): Int = capacity

    fun notifyError() {
        synchronized(lock) {
            ++expectedAbsenteeCount
            if (isReadyToLift) {
                onFinalInputPushed()
            }
        }
    }

    override fun getErrorCount(): Int = expectedAbsenteeCount

    override fun setCapacity(capacity: Int) {
        synchronized(lock) {
            if (registeredCount > capacity) {
                throw IllegalStateException("Cannot change the capacity from ${this.capacity} to $capacity, because $registeredCount items have already been registered.")
            }
            this.capacity = capacity
            if (isReadyToLift) {
                onFinalInputPushed()
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

    override fun onBarrierBlocked(barrier: Barrier<T>) {
        val liftBarrier = synchronized(lock) {
            when (barriers[barrier]) {
                null -> {
                    if (!interrupted) {
                        // barriers can be missing an interruption call races with a barrier getting blocked. Safely ignore this message,
                        // because the barrier was notified of the interruption too.
                        throw IllegalArgumentException("Barrier $barrier was never registered.")
                    }
                    false
                }
                true -> throw IllegalArgumentException("Barrier $barrier was already marked as blocked.")
                false -> {
                    barriers[barrier] = true
                    ++arrivalCount
                    isReadyToLift
                }
            }
        }

        if (liftBarrier) {
            onFinalInputPushed()
        }
    }

    override fun onBarrierInterrupted(barrier: Barrier<T>) {
        synchronized(lock) {
            when {
                isReadyToLift -> { /* no-op. If it's ready to lift, we've already dispatched another coroutine to do the lift. It can't be safely cancelled anymore. */ }
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

    private fun onFinalInputPushed() {
        /**
         * The barrier-lift action must always be done on a separate coroutine:
         *
         * - It may get called in response to `notifyError`. This is coming from a sibling's coroutine,
         *   and we cannot simply hog it for potentially executing the `onBarrierLiftAction`. Moreover, if
         *   we use one of the existing coroutines, and the orchestrator decides to retry the barrier,
         *   we'll get suspended in the middle of the `markAsFailed` loop. So just use a different one. (1)
         *
         * - It may get called in response to `setCapacity`. This will usually be called from the UI thread
         *   anyway, so we need to let go of it ASAP.
         *
         * - It may get called when the last barrier is interrupted. Same issue there as (1) above.
         */
        GlobalScope.launch(launchContext) {
            val blockedBarriers = barriers.filter { (_, blocked) ->
                blocked
            }.map { (barrier, _) ->
                barrier
            }

            val absenteeCount = barriers.size - blockedBarriers.size

            if (absenteeCount != expectedAbsenteeCount) {
                throw IllegalStateException("Expected $expectedAbsenteeCount absentees, but there were actually $absenteeCount. Something went wrong.")
            }

            val unsortedInputs = blockedBarriers.map {
                it.input ?: throw IllegalStateException("Barrier.input should've been generated by the time onBarrierBlocked was called.")
            }

            val results = if (onBarrierLiftedAction == null) {
                unsortedInputs
            } else {
                val sortedInputs = unsortedInputs.sorted()
                val sortReplayer = SortReplayer(original = unsortedInputs, sorted = sortedInputs)

                try {
                    val sortedOutputs = onBarrierLiftedAction.invoke(sortedInputs.map { it })
                    sortReplayer.reverseApplySortTransformations(sortedOutputs)
                } catch (e: Throwable) {
                    // markAsFailed will lift the barrier. If there are more attempts, the barrier may re-block.
                    // So mark it as unblocked once again.

                    // The following work can be done without acquiring the lock, because all the siblings
                    // are suspended or terminated anyway.
                    val iterator = barriers.iterator()
                    while (iterator.hasNext()) {
                        val next = iterator.next()
                        next.setValue(false)
                    }
                    arrivalCount = 0
                    expectedAbsenteeCount = 0
                    barriers.forEach { (barrier, _) ->
                        barrier.markAsFailed(BarrierLiftedActionException(this@CountedBarrierControllerImpl, e))
                    }
                    return@launch
                }
            }

            blockedBarriers.zip(results).forEach { (barrier, result) ->
                barrier.lift(result)
            }
            barriers.clear()
        }
    }

    class BarrierLiftedActionException(val source: CountedBarrierControllerImpl<*>, cause: Throwable) : RuntimeException(cause)
}
