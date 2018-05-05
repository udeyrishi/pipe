/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

import com.udeyrishi.pipe.util.SortReplayer
import com.udeyrishi.pipe.util.immutableAfterSet

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
internal class AggregatorStep<T : Any>(private val capacity: Int, private val comparator: Comparator<T>? = null, private val action: Step<List<T>>) {
    private val barriers = mutableListOf<Pair<T, BarrierStep<T>>>()
    private var processedItems: List<T>? by immutableAfterSet(null)

    suspend fun push(item: T): T {
        val (barrier, index) = addBarrier(item)

        if (index < capacity - 1) {
            // For items with indices [0, capacity - 1), suspend indefinitely until the last item is pushed.
            barrier.blockUntilLift(item)
        } else {
            onFinalItemPushed()
        }

        // By the time each coroutine reaches this, the processedItems should've been prepared (under normal situations).
        return processedItems?.let {
            it[index]
        } ?: throw IllegalStateException("The action supplied to the ${this::class.java.simpleName} was bad; it didn't return a list of size $capacity (i.e., the aggregator capacity).")
    }

    private suspend fun onFinalItemPushed() {
        // Gather all the pushed items up till now, and map them to the result via `action`.
        val unsortedInput = barriers.map { it.first }
        val sortedInput = if (comparator == null) unsortedInput else unsortedInput.sortedWith(comparator)
        val sortedProcessedItems = action(sortedInput)

        processedItems = if (sortedProcessedItems.size == capacity) {
            // If the `action` didn't misbehave (size of result == size of input == capacity), set that to the shared result.
            // If a comparator was provided, the input and its processed result may not have the same indices.
            // Since the sorting was only needed for the `action`, revert the sort, so that the indices align again.
            val sortReplayer = SortReplayer(original = unsortedInput, sorted = sortedInput)
            sortReplayer.reverseApplySortTransformations(sortedProcessedItems)
        } else {
            // Else, leave `processedItems` null to mark a failure (checked later).
            null
        }

        // Then, unblock all the other coroutines. By the time they wake up, their results (processedItems) will be prepared.
        barriers.forEach {
            it.second.lift()
        }

        // Note that a barrier for the last item was created, and pushed into `barriers` for consistency. This allows the results to be uniformly accessed
        // via index for all the items. The barrier for the last item is never actually ever blocked. But it's marked as lifted nevertheless (no-op).
    }

    private fun addBarrier(item: T): Pair<BarrierStep<T>, Int> {
        val barrier = BarrierStep<T>()
        val index = synchronized(this) {
            if (barriers.size == capacity) {
                throw IllegalStateException("Cannot push another step into the aggregator. It has reached its maximum capacity of $capacity.")
            }
            barriers.add(item to barrier)
            barriers.lastIndex
        }
        return barrier to index
    }
}