/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.testutil.DefaultTestDispatcher
import com.udeyrishi.pipe.testutil.assertIs
import com.udeyrishi.pipe.testutil.waitTill
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PipelineTest {
    @get:Rule
    val instantExecutionRule = InstantTaskExecutorRule()

    companion object {
        private lateinit var dispatcher: DefaultTestDispatcher

        @JvmStatic
        @BeforeClass
        fun setup() {
            dispatcher = DefaultTestDispatcher()
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            dispatcher.verify()
        }
    }

    @Test
    fun `DSL api works`() {
        lateinit var aggregated: List<Int>

        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

            addStep("step 1") {
                it + 1
            }

            addStep("Step 2") {
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

        jobs.map { it.state }.waitTill { (it as? State.Running.Attempting)?.step == "Barrier 1" }

        // Everyone is waiting at the barrier. Now that we know the count, we can safely update the aggregator capacity, and then lift the barrier.
        pipeline.manualBarriers[0].lift()
        pipeline.countedBarriers[0].setCapacity(3)

        jobs.map { it.state }.waitTill { it is State.Terminal }

        jobs.forEach {
            it.state.value.assertIs<State.Terminal.Success>()
        }

        jobs.forEachIndexed { index, job ->
            assertEquals(index + 1 + 2 + 3 + 1 + 4, job.result)
        }

        assertEquals(listOf(6, 7, 8), aggregated)
    }

    @Test
    fun `builder API works`() {
        lateinit var aggregated: List<Int>

        val pipeline = PipelineBuilder<Int>()
                .addStep("step 1") {
                    it + 1
                }
                .addStep("Step 2") {
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
                .setDispatcher(dispatcher)
                .build()

        val jobs = mutableListOf(
                pipeline.push(0, null),
                pipeline.push(1, null),
                pipeline.push(2, null)
        )

        jobs.map { it.state }.waitTill { (it as? State.Running.Attempting)?.step == "Barrier 1" }

        // Everyone is waiting at the barrier. Now that we know the count, we can safely update the aggregator capacity, and then lift the barrier.
        pipeline.manualBarriers[0].lift()
        pipeline.countedBarriers[0].setCapacity(3)

        jobs.map { it.state }.waitTill { it is State.Terminal.Success }

        jobs.forEachIndexed { index, job ->
            assertEquals(index + 1 + 2 + 3 + 1 + 4, job.result)
        }

        assertEquals(listOf(6, 7, 8), aggregated)
    }

    @Test
    fun `interrupting one job does not interrupt others`() {
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

            addManualBarrier("some barrier")

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.interrupt()

        pipeline.manualBarriers[0].lift()

        job1.state.waitTill { it is State.Terminal.Failure }

        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
        assertNull(job1.result)

        job2.state.waitTill { it is State.Terminal }

        assertEquals(State.Terminal.Success, job2.state.value)
        assertEquals(3, job2.result)
    }

    @Test
    fun `interrupting one job blocked on a manual barrier does not interrupt other jobs blocked on the same barrier`() {
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

            addManualBarrier("some barrier")

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.state.waitTill { (it as? State.Running.Attempting)?.step == "some barrier" }

        job1.interrupt()

        pipeline.manualBarriers[0].lift()

        listOf(job1.state, job2.state).waitTill { it is State.Terminal }

        job1.state.value.assertIs<State.Terminal.Failure>()
        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
        assertNull(job1.result)

        assertEquals(State.Terminal.Success, job2.state.value)
        assertEquals(3, job2.result)
    }

    @Test
    fun `interrupting one job blocked on a counted barrier also interrupts the sibling jobs`() {
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

            addCountedBarrier("some barrier", capacity = 3)

            addStep("my step", attempts = 1L) {
                it + 1
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        job1.state.waitTill { (it as? State.Running.Attempting)?.step == "some barrier" }

        job1.interrupt()

        listOf(job1.state, job2.state).waitTill { it is State.Terminal.Failure }

        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
        assertNull(job1.result)

        (job2.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
        assertNull(job2.result)
    }

    @Test
    fun `error in one job does not affect its siblings if the error appears before the counted barrier`() {
        lateinit var barrierList: List<Int>
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

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

        listOf(job1.state, job2.state, job3.state).waitTill { it is State.Terminal }

        job1.state.value.assertIs<State.Terminal.Failure>()
        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.StepOutOfAttemptsException>()
        (job1.state.value as State.Terminal.Failure).cause.cause.assertIs<Orchestrator.StepFailureException>()
        (job1.state.value as State.Terminal.Failure).cause.cause?.cause.assertIs<RuntimeException>()
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        job2.state.value.assertIs<State.Terminal.Success>()
        assertEquals(4, job2.result)

        job3.state.value.assertIs<State.Terminal.Success>()
        assertEquals(5, job3.result)

        // The arg to the `onBarrierLiftedAction` only included the successful passengers
        assertEquals(listOf(3, 4), barrierList)
        assertEquals(1, pipeline.countedBarriers[0].getErrorCount())
        assertEquals(3, pipeline.countedBarriers[0].getCapacity())
    }

    @Test
    fun `error in one job does not affect its siblings if the error appears after the counted barrier`() {
        lateinit var barrierList: List<Int>
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

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

        listOf(job1.state, job2.state, job3.state).waitTill { it is State.Terminal }

        job1.state.value.assertIs<State.Terminal.Failure>()
        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.StepOutOfAttemptsException>()
        (job1.state.value as State.Terminal.Failure).cause.cause.assertIs<Orchestrator.StepFailureException>()
        (job1.state.value as State.Terminal.Failure).cause.cause?.cause.assertIs<RuntimeException>()
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        job2.state.value.assertIs<State.Terminal.Success>()
        assertEquals(4, job2.result)

        job3.state.value.assertIs<State.Terminal.Success>()
        assertEquals(5, job3.result)

        assertEquals(listOf(2, 3, 4), barrierList)
        assertEquals(3, pipeline.countedBarriers[0].getCapacity())
    }

    @Test
    fun `error in one job does not affect its sibling if they share a manual barrier`() {
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)

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

        job1.state.waitTill { it is State.Terminal }

        pipeline.manualBarriers[0].lift()

        listOf(job2.state, job3.state).waitTill { it is State.Terminal }

        job1.state.value.assertIs<State.Terminal.Failure>()
        (job1.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.StepOutOfAttemptsException>()
        (job1.state.value as State.Terminal.Failure).cause.cause.assertIs<Orchestrator.StepFailureException>()
        (job1.state.value as State.Terminal.Failure).cause.cause?.cause.assertIs<RuntimeException>()
        assertEquals("BOOM!", (job1.state.value as State.Terminal.Failure).cause.cause?.cause?.message)
        assertNull(job1.result)

        job2.state.value.assertIs<State.Terminal.Success>()
        assertEquals(4, job2.result)

        job3.state.value.assertIs<State.Terminal.Success>()
        assertEquals(5, job3.result)
    }

    @Test
    fun `retries failures in counted barrier onBarrierLiftedAction`() {
        var attemptNum = 0
        val pipeline = buildPipeline<Int> {
            setDispatcher(dispatcher)
            addCountedBarrier(name = "My barrier", capacity = 2, attempts = 5) { input ->
                if (++attemptNum < 5) {
                    throw RuntimeException("BOOM!")
                }
                val total = input.sum()
                input.map { it + total }
            }
        }

        val job1 = pipeline.push(1, null)
        val job2 = pipeline.push(2, null)

        listOf(job1.state, job2.state).waitTill { it is State.Terminal.Success }

        assertEquals(5, attemptNum)

        assertEquals(4, job1.result)
        assertEquals(5, job2.result)
    }
}
