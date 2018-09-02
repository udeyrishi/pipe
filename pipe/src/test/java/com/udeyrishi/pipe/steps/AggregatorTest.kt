/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

import com.udeyrishi.pipe.testutil.Repeat
import com.udeyrishi.pipe.testutil.RepeatRule
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class AggregatorTest {
    @Rule
    @JvmField
    val repeatRule = RepeatRule()

    // Can verify the exception via the @Test mechanism, because the exception will be thrown in the main thread.
    @Test(expected = IllegalStateException::class)
    fun checksForCapacityOverflow() {
        val aggregator = AggregatorImpl<String>(name = "Some aggregator", capacity = 2, ordered = false) { it }
        val job1 = launch {
            aggregator.push("apple")
        }
        val job2 = launch {
            aggregator.push("bob")
        }

        runBlocking {
            job1.join()
            job2.join()
            aggregator.push("fail")
        }
    }

    // Cannot verify the exception via the @Test mechanism, because the exception will be thrown in different coroutine.
    @Test
    fun checksForBadAggregatorActions() {
        val lock = Any()

        val aggregator = AggregatorImpl<String>(name = "Some aggregator", capacity = 2, ordered = false) {
            listOf("just 1 item in result")
        }

        var exceptionCount = 0

        val job1 = launch {
            try {
                aggregator.push("apple")
            } catch (e: IllegalStateException) {
                assertEquals("The aggregationAction supplied to the ${AggregatorImpl::class.java.simpleName} was bad; it didn't return a list of size 2 (i.e., the aggregator capacity).", e.message)
                synchronized(lock) {
                    exceptionCount++
                }
            }
        }
        val job2 = launch {
            try {
                aggregator.push("bob")
            } catch (e: IllegalStateException) {
                assertEquals("The aggregationAction supplied to the ${AggregatorImpl::class.java.simpleName} was bad; it didn't return a list of size 2 (i.e., the aggregator capacity).", e.message)
                synchronized(lock) {
                    exceptionCount++
                }
            }
        }

        runBlocking {
            job1.join()
            job2.join()
        }

        assertEquals(2, exceptionCount)
    }

    @Test
    @Repeat
    fun unorderedWorks() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 5, ordered = false) {
            aggregatorArgs = it
            it.map { it + 1 }
        }
        val deferredResults = (0 until 5).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }
        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..5).toList(), incrementedResults)
        assertEquals((0 until 5).toList(), aggregatorArgs?.sorted())
    }

    @Test
    @Repeat
    fun orderedWorks() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 5, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }
        val deferredResults = (0 until 5).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }
        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..5).toList(), incrementedResults)
        assertEquals((0 until 5).toList(), aggregatorArgs)
    }

    @Test
    fun canUpdateCapacityToABiggerValueWhenBlocked() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 6, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }
        val deferredResults = (0 until 5).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }.toMutableList()

        aggregator.capacity = 7

        (5 until 7).mapTo(deferredResults) {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }

        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..7).toList(), incrementedResults)
        assertEquals((0 until 7).toList(), aggregatorArgs)
    }

    @Test
    fun canUpdateCapacityToALowerValueWhenBlocked() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 5, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }
        val deferredResults = (0 until 3).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }

        aggregator.capacity = 3

        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..3).toList(), incrementedResults)
        assertEquals((0 until 3).toList(), aggregatorArgs)
    }

    @Test
    fun canUpdateCapacityToABiggerValueBeforeStart() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 3, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }

        aggregator.capacity = 5

        val deferredResults = (0 until 5).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }
        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..5).toList(), incrementedResults)
        assertEquals((0 until 5).toList(), aggregatorArgs)
    }

    @Test
    fun canUpdateCapacityToALowerValueBeforeStart() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 5, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }

        aggregator.capacity = 3

        val deferredResults = (0 until 3).map {
            async {
                Thread.sleep(it + 5L)
                aggregator.push(it)
            }
        }
        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..3).toList(), incrementedResults)
        assertEquals((0 until 3).toList(), aggregatorArgs)
    }

    @Test
    fun canUpdateCapacityToArrivalCount() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 7, ordered = true) {
            aggregatorArgs = it
            it.map { it + 1 }
        }

        val deferredResults = (0 until 5).map {
            async {
                aggregator.push(it)
            }
        }

        while (aggregator.arrivalCount < 5) {
            Thread.sleep(50)
        }

        aggregator.capacity = 5

        val incrementedResults = runBlocking {
            deferredResults.map {
                it.await()
            }
        }
        assertEquals((1..5).toList(), incrementedResults)
        assertEquals((0 until 5).toList(), aggregatorArgs)
    }

    @Test(expected = IllegalStateException::class)
    fun safelyDetectsInabilityToUpdateCapacityToLessThanArrivalCount() {
        val aggregator = AggregatorImpl<Int>(name = "Some aggregator", capacity = 7, ordered = true) {
            it.map { it + 1 }
        }

        (0 until 5).map {
            async {
                aggregator.push(it)
            }
        }

        while (aggregator.arrivalCount < 5) {
            Thread.sleep(50)
        }

        aggregator.capacity = 3
    }
}
