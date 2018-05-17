/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

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
        val aggregator = Aggregator<String>(capacity = 2, ordered = false) { it }
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
        val aggregator = Aggregator<String>(capacity = 2, ordered = false) {
            listOf("just 1 item in result")
        }

        var exceptionCount = 0

        val job1 = launch {
            try {
                aggregator.push("apple")
            } catch (e: IllegalStateException) {
                assertEquals("The aggregationAction supplied to the ${Aggregator::class.java.simpleName} was bad; it didn't return a list of size 2 (i.e., the aggregator capacity).", e.message)
                synchronized(this@AggregatorTest) {
                    exceptionCount++
                }
            }
        }
        val job2 = launch {
            try {
                aggregator.push("bob")
            } catch (e: IllegalStateException) {
                assertEquals("The aggregationAction supplied to the ${Aggregator::class.java.simpleName} was bad; it didn't return a list of size 2 (i.e., the aggregator capacity).", e.message)
                synchronized(this@AggregatorTest) {
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
    fun worksWhenNoComparatorProvided() {
        var aggregatorArgs: List<Int>? = null
        val aggregator = Aggregator<Int>(capacity = 5, ordered = false) {
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
        val aggregator = Aggregator<Int>(capacity = 5, ordered = true) {
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
}