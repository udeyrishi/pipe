/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.testutil.DefaultTestDispatcher
import com.udeyrishi.pipe.testutil.Repeat
import com.udeyrishi.pipe.testutil.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class PipelineTest {
    @get:Rule
    val instantExecutionRule = InstantTaskExecutorRule()

    @get:Rule
    val timeoutRule = Timeout(25, TimeUnit.SECONDS)

    @get:Rule
    val repeatRule = RepeatRule()

    @Test
    fun `DSL api works`() {
        val lock = Any()
        lateinit var aggregated: List<Int>

        var barrierReachedCount = 0

        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addStep("step 1") {
                it + 1
            }

            addStep("Step 2") {
                synchronized(lock) {
                    barrierReachedCount++
                }
                it + 2
            }

            addManualBarrier("Barrier 1")

            addStep("Step 3") {
                it + 3
            }

            addCountedBarrier("Aggregator 1", capacity = 1000000) { aggregatedList ->
                aggregated = aggregatedList
                aggregatedList.map { it + 1 }
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
        pipeline.manualBarriers[0].lift()
        pipeline.countedBarriers[0].setCapacity(3)

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

    @Test
    fun `builder API works`() {
        val lock = Any()
        lateinit var aggregated: List<Int>

        var barrierReachedCount = 0

        val pipeline = Pipeline.Builder<Int>()
                .addStep("step 1") {
                    it + 1
                }
                .addStep("Step 2") {
                    synchronized(lock) {
                        barrierReachedCount++
                    }
                    it + 2
                }
                .addManualBarrier("Barrier 1")
                .addStep("Step 3") {
                    it + 3
                }
                .addCountedBarrier("Aggregator 1", capacity = 1000000) { aggregatedList ->
                    aggregated = aggregatedList
                    aggregatedList.map { it + 1 }
                }
                .addStep("Step 4") {
                    it + 4
                }
                .build(InMemoryRepository(), DefaultTestDispatcher)

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
        pipeline.manualBarriers[0].lift()
        pipeline.countedBarriers[0].setCapacity(3)

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

    @Test
    fun `interrupting one job does not interrupt others`() {
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addManualBarrier("some barrier")

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.interrupt()
        job2.start()

        pipeline.manualBarriers[0].lift()

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.OrchestratorInterruptedException)
        assertNull(job1.result)

        while (job2.state.value !is State.Terminal) {
            Thread.sleep(10)
        }

        assertEquals(State.Terminal.Success, job2.state.value)
        assertEquals(3, job2.result)
    }

    @Test
    fun `interrupting one job blocked on a manual barrier does not interrupt other jobs blocked on the same barrier`() {
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addManualBarrier("some barrier")

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.start()
        job2.start()

        while ((job1.state.value as? State.Running.Attempting)?.step != "some barrier") {
            Thread.sleep(10)
        }

        job1.interrupt()

        pipeline.manualBarriers[0].lift()

        while (job1.state.value !is State.Terminal || job2.state.value !is State.Terminal) {
            Thread.sleep(10)
        }

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.OrchestratorInterruptedException)
        assertNull(job1.result)

        assertEquals(State.Terminal.Success, job2.state.value)
        assertEquals(3, job2.result)
    }

    @Repeat
    @Test
    fun `interrupting one job blocked on a counted barrier also interrupts the sibling jobs`() {
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addCountedBarrier("some barrier", capacity = 3)

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.start()
        job2.start()

        while ((job1.state.value as? State.Running.Attempting)?.step != "some barrier") {
            Thread.sleep(10)
        }

        job1.interrupt()

        while (job1.state.value !is State.Terminal || job2.state.value !is State.Terminal) {
            Thread.sleep(10)
        }

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.OrchestratorInterruptedException)
        assertNull(job1.result)

        assertTrue(job2.state.value is State.Terminal.Failure)
        assertTrue((job2.state.value as State.Terminal.Failure).cause is Orchestrator.OrchestratorInterruptedException)
        assertNull(job2.result)
    }

    @Test
    fun `error in one job reduces the counted barrier capacity such that its siblings may continue working`() {
        lateinit var barrierList: List<Int>
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addStep("my step 1", attempts = 1L) {
                if (it == 1) {
                    throw RuntimeException("BOOM!")
                }
                it + 1
            }

            addCountedBarrier("some barrier", capacity = 3) {
                barrierList = it
                it
            }

            addStep("my step 2", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)
        val job3 = pipeline.push(3, null)

        job1.start()
        job2.start()
        job3.start()

        while (listOf(job1, job2, job3).any { it.state.value !is State.Terminal }) {
            Thread.sleep(100)
        }

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.StepOutOfAttemptsException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause is Orchestrator.StepFailureException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause?.cause is RuntimeException)
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        assertTrue(job2.state.value is State.Terminal.Success)
        assertEquals(4, job2.result)

        assertTrue(job3.state.value is State.Terminal.Success)
        assertEquals(5, job3.result)

        // The capacity was automatically reduced, and the arg to the `onBarrierLiftedAction` only included the successful passengers
        assertEquals(listOf(3, 4), barrierList)
        assertEquals(2, pipeline.countedBarriers[0].getCapacity())
    }

    @Test
    fun `error in one job does not affect its siblings if the error appears after the counted barrier`() {
        lateinit var barrierList: List<Int>
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addStep("my step 1", attempts = 1L) {
                it + 1
            }

            addCountedBarrier("some barrier", capacity = 3) {
                barrierList = it
                it
            }

            addStep("my step 2", attempts = 1L) {
                if (it == 2) {
                    throw RuntimeException("BOOM!")
                }
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)
        val job3 = pipeline.push(3, null)

        job1.start()
        job2.start()
        job3.start()

        while (listOf(job1, job2, job3).any { it.state.value !is State.Terminal }) {
            Thread.sleep(100)
        }

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.StepOutOfAttemptsException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause is Orchestrator.StepFailureException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause?.cause is RuntimeException)
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        assertTrue(job2.state.value is State.Terminal.Success)
        assertEquals(4, job2.result)

        assertTrue(job3.state.value is State.Terminal.Success)
        assertEquals(5, job3.result)

        assertEquals(listOf(2, 3, 4), barrierList)
        assertEquals(3, pipeline.countedBarriers[0].getCapacity())
    }

    @Test
    fun `error in one job does not affect its sibling if they share a manual barrier`() {
        val pipeline = buildPipeline<Int>(InMemoryRepository(), DefaultTestDispatcher) {
            addStep("my step 1", attempts = 1L) {
                if (it == 1) {
                    throw RuntimeException("BOOM!")
                }
                it + 1
            }

            addManualBarrier("some barrier")

            addStep("my step 2", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)
        val job3 = pipeline.push(3, null)

        job1.start()
        job2.start()
        job3.start()

        while (job1.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        pipeline.manualBarriers[0].lift()

        while (listOf(job2, job3).any { it.state.value !is State.Terminal }) {
            Thread.sleep(100)
        }

        assertTrue(job1.state.value is State.Terminal.Failure)
        assertTrue((job1.state.value as State.Terminal.Failure).cause is Orchestrator.StepOutOfAttemptsException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause is Orchestrator.StepFailureException)
        assertTrue((job1.state.value as State.Terminal.Failure).cause.cause?.cause is RuntimeException)
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        assertTrue(job2.state.value is State.Terminal.Success)
        assertEquals(4, job2.result)

        assertTrue(job3.state.value is State.Terminal.Success)
        assertEquals(5, job3.result)
    }
}
