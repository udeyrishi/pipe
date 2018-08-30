/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.steps.Aggregator
import com.udeyrishi.pipe.steps.Barrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class PipelineTest {
    @get:Rule
    val instantExecutionRule = InstantTaskExecutorRule()

    @Test
    fun works() {
        val lock = Any()
        lateinit var barrier: Barrier
        lateinit var aggregator: Aggregator
        lateinit var aggregated: List<Int>

        var barrierReachedCount = 0

        val pipeline = buildPipeline<Int>(ordered = true, repository = InMemoryRepository()) {
            addStep("step 1") {
                it + 1
            }

            addStep("Step 2") {
                synchronized(lock) {
                    barrierReachedCount++
                }
                it + 2
            }

            barrier = addBarrier()

            addStep("Step 3") {
                it + 3
            }

            aggregator = addAggregator(capacity = 1000000) {
                aggregated = it
                it.map { it + 1 }
            }

            addStep("Step 4") {
                it + 4
            }
        }

        val jobs = mutableListOf(
                pipeline.push(0, null),
                pipeline.push(1, null),
                pipeline.push(2, null)
        )

        jobs.forEach {
            it.start()
        }

        while (barrierReachedCount < 3) {
            Thread.sleep(100)
        }

        jobs.forEach {
            assertTrue(it.state.value is State.Running.Attempting)
        }

        // Everyone is waiting at the barrier. Now that we know the count, we can safely update the aggregator capacity, and then lift the barrier.
        barrier.lift()
        aggregator.capacity = 3

        while (jobs.any { it.state.value !is State.Terminal }) {
            Thread.sleep(100)
        }

        // Everyone is done

        jobs.forEach {
            assertTrue(it.state.value === State.Terminal.Success)
        }

        jobs.forEachIndexed { index, job ->
            assertEquals(index + 1 + 2 + 3 + 1 + 4, job.result)
        }

        assertEquals(listOf(6, 7, 8), aggregated)
    }
}
