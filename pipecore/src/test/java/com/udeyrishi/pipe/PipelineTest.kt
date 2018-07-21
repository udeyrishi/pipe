/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.state.State
import com.udeyrishi.pipe.steps.Aggregator
import com.udeyrishi.pipe.steps.Barrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class PipelineTest {
    @Test
    fun works() {
        lateinit var barrier: Barrier<Int>
        lateinit var aggregator: Aggregator<Pipeline.Passenger<Int>>
        lateinit var aggregated: List<Int>

        var barrierReachedCount = 0

        val pipeline = buildPipeline<Int>(ordered = true, repository = InMemoryRepository()) {
            addStep("step 1") {
                it + 1
            }

            addStep("Step 2") {
                synchronized(this@PipelineTest) {
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


        val orchestrators = mutableListOf(
            pipeline.push(0, null),
            pipeline.push(1, null),
            pipeline.push(2, null)
        )

        orchestrators.forEach {
            it.start()
        }

        while (barrierReachedCount < 3) {
            Thread.sleep(100)
        }

        orchestrators.forEach {
            assertTrue(it.state is State.Running.Attempting)
        }

        // Everyone is waiting at the barrier. Now that we know the count, we can safely update the aggregator capacity, and then lift the barrier.
        barrier.lift()
        aggregator.updateCapacity(3)

        while (orchestrators.any { it.state !is State.Terminal }) {
            Thread.sleep(100)
        }

        // Everyone is done

        orchestrators.forEach {
            assertTrue(it.state is State.Terminal.Success)
        }

        orchestrators.forEachIndexed { index, orchestrator ->
            assertEquals(index + 1 + 2 + 3 + 1 + 4, orchestrator.result?.data)
        }

        assertEquals(listOf(6, 7, 8), aggregated)
    }
}