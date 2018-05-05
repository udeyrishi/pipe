/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

internal class SortReplayer<T : Any>(original: List<T>, sorted: List<T>) {
    private val transformations: Map<Int, Int>?

    init {
        if (original.size != sorted.size) {
            throw IllegalArgumentException("original and sorted must be lists of the same size and same elements, just in different orders.")
        }

        transformations = if (original === sorted) {
            null
        } else {

            // TODO: optimize this n^2
            HashMap<Int, Int>().apply {
                original.forEachIndexed { unsortedIndex, item ->
                    val sortedIndex = sorted.indexOfFirst { it === item }
                    if (sortedIndex < 0) {
                        throw IllegalArgumentException("original and sorted must be lists of the same size and same elements, just in different orders.")
                    }
                    put(unsortedIndex, sortedIndex)
                }
            }
        }
    }

    fun <R> applySortTransformations(unsorted: List<R>): List<R> {
        if (transformations == null) {
            return unsorted.map { it }
        }

        if (unsorted.size != transformations.size) {
            throw IllegalArgumentException("unsorted's size must be the same as the original list that was used to generate the SortMap.")
        }

        val sorted = MutableList<R?>(unsorted.size) { null }

        transformations.entries.forEach { (unsortedIndex, sortedIndex) ->
            sorted[sortedIndex] = unsorted[unsortedIndex]
        }

        return sorted.map { it!! }
    }

    fun <R> reverseApplySortTransformations(sorted: List<R>): List<R> {
        if (transformations == null) {
             return sorted.map { it }
        }

        if (sorted.size != transformations.size) {
            throw IllegalArgumentException("sorted's size must be the same as the original list that was used to generate the SortMap.")
        }

        return List(sorted.size) { unsortedIndex ->
            val sortedIndex = transformations[unsortedIndex] ?: throw IllegalStateException("Something went wrong. Unsorted index $unsortedIndex not found in the sortMap.")
            sorted[sortedIndex]
        }
    }
}