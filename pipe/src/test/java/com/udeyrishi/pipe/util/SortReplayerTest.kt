/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SortReplayerTest {
    @Test
    fun forwardApplyWorks() {
        val original = listOf(4, 0, 2, 3, 1)
        val comparator = Comparator<Int> { o1, o2 -> o1.compareTo(o2) }
        val sorted = original.sortedWith(comparator)
        val sortReplayer = SortReplayer(original = original, sorted = sorted)

        val originalStrings = listOf("four", "zero", "two", "three", "one")
        val sortedStrings = sortReplayer.applySortTransformations(originalStrings)
        assertEquals(listOf("zero", "one", "two", "three", "four"), sortedStrings)
    }

    @Test
    fun reverseApplyWorks() {
        val original = listOf(4, 0, 2, 3, 1)
        val comparator = Comparator<Int> { o1, o2 -> o1.compareTo(o2) }
        val sorted = original.sortedWith(comparator)
        val sortReplayer = SortReplayer(original = original, sorted = sorted)

        val sortedStrings = listOf("zero", "one", "two", "three", "four")
        val originalStrings = sortReplayer.reverseApplySortTransformations(sortedStrings)
        assertEquals(listOf("four", "zero", "two", "three", "one"), originalStrings)
    }
}
