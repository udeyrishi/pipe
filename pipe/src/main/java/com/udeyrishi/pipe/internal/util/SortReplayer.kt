/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

internal class SortReplayer<T : Any>(original: List<T>, sorted: List<T>) {
    private val transformations: Map<Int, Int>?
    private val size: Int

    init {
        if (original.size != sorted.size) {
            throw IllegalArgumentException("original and sorted must be lists of the same size and same elements, just in different orders.")
        }

        transformations = if (original === sorted) {
            null
        } else {
            val positionMapSorted = sorted.mapIndexed { index, item -> Pair(item, index) }.toMap()

            original.mapIndexed { unsortedIndex, item ->
                unsortedIndex to (positionMapSorted[item]
                        ?: throw IllegalArgumentException("original and sorted must be lists of the same size and same elements, just in different orders."))
            }.toMap()
        }

        size = original.size
    }

    fun <R> applySortTransformations(unsorted: List<R>): List<R> {
        if (unsorted.size != size) {
            throw IllegalArgumentException("unsorted's size must be the same as the original list that was used to generate the SortMap.")
        }

        if (transformations == null) {
            return unsorted.map { it }
        }

        val sorted = MutableList<R?>(unsorted.size) { null }
        transformations.entries.forEach { (unsortedIndex, sortedIndex) ->
            sorted[sortedIndex] = unsorted[unsortedIndex]
        }
        return sorted.map { it!! }
    }

    fun <R> reverseApplySortTransformations(sorted: List<R>): List<R> {
        if (sorted.size != size) {
            throw IllegalArgumentException("sorted's size must be the same as the original list that was used to generate the SortMap.")
        }

        if (transformations == null) {
            return sorted.map { it }
        }

        return List(sorted.size) { unsortedIndex ->
            val sortedIndex = transformations[unsortedIndex]
                    ?: throw IllegalStateException("Something went wrong. Unsorted index $unsortedIndex not found in the sortMap.")
            sorted[sortedIndex]
        }
    }
}
