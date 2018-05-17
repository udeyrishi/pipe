/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.util.SortReplayer
import com.udeyrishi.pipe.util.immutableAfterSet

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class Aggregator<T : Comparable<T>>(capacity: Int, private val ordered: Boolean = true, private val aggregationAction: Step<List<T>>) {
    private val barriers = mutableListOf<Pair<T, Barrier<T>>>()
    private var outputs: List<T>? by immutableAfterSet(null)

    var capacity: Int = capacity
        set(value) {
            synchronized(this) {
                if (arrivalCount >= value) {
                    throw IllegalStateException("Cannot change the capacity from $field to $value, because there are already $arrivalCount items in the aggregator.")
                }
                field = value
            }
        }

    val arrivalCount: Int
        get() = barriers.size

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
        // via index for all the inputs. The barrier for the last input is never actually ever blocked. But it's marked as lifted nevertheless (no-op).
    }

    private fun addBarrier(input: T): Pair<Barrier<T>, Int> {
        val barrier = Barrier<T>()
        val index = synchronized(this) {
            if (arrivalCount == capacity) {
                throw IllegalStateException("Cannot push another step into the aggregator. It has reached its maximum capacity of $capacity.")
            }
            barriers.add(input to barrier)
            barriers.lastIndex
        }
        return barrier to index
    }
}