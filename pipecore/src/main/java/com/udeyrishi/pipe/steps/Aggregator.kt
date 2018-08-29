/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

import com.udeyrishi.pipe.util.SortReplayer
import com.udeyrishi.pipe.util.immutableAfterSet
import kotlinx.coroutines.experimental.launch

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class Aggregator<T : Comparable<T>>(private var capacity: Int, private val ordered: Boolean = true, private val aggregationAction: Step<List<T>>) {
    private val barriers = mutableListOf<Pair<T, Barrier<T>>>()
    private var outputs: List<T>? by immutableAfterSet(null)
    private val lock = Any()

    val arrivalCount: Int
        get() = barriers.size

    fun updateCapacity(newCapacity: Int) {
        synchronized(lock) {
            if (arrivalCount > newCapacity) {
                throw IllegalStateException("Cannot change the capacity from $capacity to $newCapacity, because there are already $arrivalCount items in the aggregator.")
            }
            capacity = newCapacity
            if (arrivalCount == newCapacity) {
                // We now need to retroactively mark the last arrived input as the final input. Usually, the last arrival's coroutine can be reused for aggregation purposes, but it's blocked
                // on the barrier now. So temporarily create a new one to do the aggregation + unblocking work.
                launch {
                    onFinalInputPushed()
                }
            }
        }
    }

    internal suspend fun push(input: T): T {
        val (barrier, index) = addBarrier(input)

        if (index < capacity - 1) {
            // For inputs with indices [0, capacity - 1), suspend indefinitely until the last input is pushed.
            barrier.blockUntilLift(input)
        } else {
            onFinalInputPushed()
        }

        // By the time each coroutine reaches this, the outputs should've been prepared (under normal situations).
        return outputs?.let {
            it[index]
        } ?: throw IllegalStateException("The aggregationAction supplied to the ${this::class.java.simpleName} was bad; it didn't return a list of size $capacity (i.e., the aggregator capacity).")
    }

    private suspend fun onFinalInputPushed() {
        // Gather all the pushed inputs up till now, and map them to the outputs via `aggregationAction`.
        val unsortedInput = barriers.map { it.first }
        val sortedInput = if (ordered) unsortedInput.sorted() else unsortedInput
        val sortedOutputs = aggregationAction(sortedInput.map { it })

        outputs = if (sortedOutputs.size == capacity) {
            // If the `aggregationAction` didn't misbehave (size of result == size of input == capacity), set that to the shared result.
            // If a comparator was provided, the input and its processed result may not have the same indices.
            // Since the sorting was only needed for the `aggregationAction`, revert the sort, so that the indices align again.
            val sortReplayer = SortReplayer(original = unsortedInput, sorted = sortedInput)
            sortReplayer.reverseApplySortTransformations(sortedOutputs)
        } else {
            // Else, leave `outputs` null to mark a failure (checked later).
            null
        }

        // Then, unblock all the other coroutines. By the time they wake up, their outputs will be prepared.
        barriers.forEach {
            it.second.lift()
        }

        // Note that a barrier for the last input was created, and pushed into `barriers` for consistency. This allows the results to be uniformly accessed
        // via index for all the inputs. The barrier for the last input is rarely actually ever blocked. In such case, it's marked as lifted nevertheless (no-op).

        // The only time last barrier is actually lifted is if the capacity is updated such that the arrivalCount == the new capacity. In that case, we need to
        // retroactively mark the last arrival as the final item.
    }

    private fun addBarrier(input: T): Pair<Barrier<T>, Int> {
        val barrier = Barrier<T>()
        val index = synchronized(lock) {
            if (arrivalCount == capacity) {
                throw IllegalStateException("Cannot push another step into the aggregator. It has reached its maximum capacity of $capacity.")
            }
            barriers.add(input to barrier)
            barriers.lastIndex
        }
        return barrier to index
    }
}