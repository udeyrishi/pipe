/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.udeyrishi.pipe.State
import com.udeyrishi.pipe.internal.steps.InterruptibleStep
import com.udeyrishi.pipe.internal.steps.StepDescriptor
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.testutil.DefaultTestDispatcher
import com.udeyrishi.pipe.testutil.assertIs
import com.udeyrishi.pipe.testutil.createMockLifecycleOwner
import com.udeyrishi.pipe.testutil.waitTill
import com.udeyrishi.pipe.util.Identifiable
import com.udeyrishi.pipe.util.Logger
import kotlinx.coroutines.runBlocking
import net.jodah.concurrentunit.Waiter
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
class OrchestratorTest {

    private data class IdentifiableString(val data: String, override val uuid: UUID = UUID.randomUUID()) : Identifiable

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
    fun startsWithScheduledState() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator(), dispatcher.createEffectiveContext())
        assertEquals(State.Scheduled, orchestrator.state.value)
    }

    @Test
    fun goesToCompletionWhenNoSteps() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator(), dispatcher.createEffectiveContext())
        assertNull(orchestrator.result)
        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Success }
        assertEquals(input, orchestrator.result)
    }

    @Test
    fun executesStepsWithCorrectStateChanges() {
        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                IdentifiableString("${it.data}->$i", it.uuid)
            }
        }

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertEquals(State.Scheduled, orchestrator.state.value)
                1 -> {
                    // step 0 start
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5, 7, 9, 11 -> {
                    // steps 1-5 start
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step${(i - 1) / 2}", (newState as State.Running).step)
                }
                4, 6, 8, 10, 12 -> {
                    // steps 1-5 end
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    assertEquals("step${(i - 2) / 2}", (newState as State.Running).step)
                }
                13 -> {
                    // pipeline completion
                    assertEquals(State.Terminal.Success, newState)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Success }
        assertEquals(IdentifiableString("in->0->1->2->3->4->5", input.uuid), orchestrator.result)
    }

    @Test
    fun handlesFailuresCorrectly() {
        val error = RuntimeException("something went wrong")
        var called = 0
        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                // Should not call this step more than once
                assertEquals(i, called++)
                when {
                    i == 2 -> throw error
                    i > 2 -> throw AssertionError("Step 2 should've been the last one.")
                    else -> IdentifiableString("${it.data}->$i", it.uuid)
                }
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertEquals(State.Scheduled, orchestrator.state.value)
                1 -> {
                    // step 0 start
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5 -> {
                    // step starts
                    newState.assertIs<State.Running.Attempting>()
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                4 -> {
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                6 -> {
                    // step 2 failure
                    newState.assertIs<State.Running.AttemptFailed>()
                    assertEquals("step2", (newState as State.Running).step)
                }
                7 -> {
                    // step 2 retry
                    newState.assertIs<State.Terminal.Failure>()
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Failure }
        assertNull(orchestrator.result)

        (orchestrator.state.value as State.Terminal.Failure).let {
            it.cause.assertIs<Orchestrator.StepOutOfAttemptsException>()
            it.cause.cause.assertIs<Orchestrator.StepFailureException>()
            assertEquals(error, it.cause.cause?.cause)
        }
    }

    @Test
    fun retriesWhenNeeded() {
        val error = RuntimeException("something went wrong")
        var failureCount = 0
        var called = 0
        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 2) {
                when {
                    i == 2 -> assertTrue(listOf(2, 3).contains(called++))
                    i < 2 -> assertEquals(i, called++)
                    else -> assertEquals(i + 1, called++)
                }

                if (i == 2 && failureCount++ < 1) {
                    throw error
                }
                IdentifiableString("${it.data}->$i", it.uuid)
            }
        }

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertEquals(State.Scheduled, orchestrator.state.value)
                1 -> {
                    // step 0 start
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5, 9, 11, 13 -> {
                    // steps; starts. Skips 6 because of the failures.
                    newState.assertIs<State.Running.Attempting>()
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                6 -> {
                    // step 2 failure
                    newState.assertIs<State.Running.AttemptFailed>()
                    assertEquals("step2", (newState as State.Running).step)
                    (newState as State.Running.AttemptFailed).cause.assertIs<Orchestrator.StepFailureException>()
                    assertEquals(error, (newState.cause as Orchestrator.StepFailureException).cause)
                }
                7 -> {
                    // step 2 retry
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step2", (newState as State.Running).step)
                }
                4, 8, 10, 12, 14 -> {
                    // step 1, 3, 4, and 5 were successful the first time. 2 took a retry.
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                15 -> {
                    // pipeline completion
                    assertEquals(State.Terminal.Success, newState)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Success }
        assertEquals(IdentifiableString("in->0->1->2->3->4->5", input.uuid), orchestrator.result)
    }

    @Test
    fun respectsMaxAttempts() {
        val error = RuntimeException("something went wrong")
        var step2Called = 0

        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 5) {
                when {
                    i == 2 -> {
                        step2Called++
                        throw error
                    }
                    i > 2 -> throw AssertionError("Step 2 should've been the last one.")
                    else -> IdentifiableString("${it.data}->$i", it.uuid)
                }
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertEquals(State.Scheduled, orchestrator.state.value)
                1 -> {
                    // step 0 start
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5 -> {
                    // step starts
                    newState.assertIs<State.Running.Attempting>()
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                4 -> {
                    newState.assertIs<State.Running.AttemptSuccessful>()
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                6, 8, 10, 12, 14 -> {
                    // step 2 failure
                    newState.assertIs<State.Running.AttemptFailed>()
                    assertEquals("step2", (newState as State.Running).step)
                }
                7, 9, 11, 13 -> {
                    // step 2 retry
                    newState.assertIs<State.Running.Attempting>()
                    assertEquals("step2", (newState as State.Running).step)
                }
                15 -> {
                    newState.assertIs<State.Terminal.Failure>()
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Failure }
        assertNull(orchestrator.result)

        (orchestrator.state.value as State.Terminal.Failure).let {
            it.cause.assertIs<Orchestrator.StepOutOfAttemptsException>()
            it.cause.cause.assertIs<Orchestrator.StepFailureException>()
            assertEquals(error, it.cause.cause?.cause)
        }

        assertEquals(5, step2Called)
    }

    @Test
    fun interruptsCorrectly() {
        val lastStepIndex = 4
        val interruptionWaiter = Waiter()
        val lastStepContinueWaiter = Waiter()
        val steps = (0..1000).asSequence().map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                interruptionWaiter.resume()
                if (i == lastStepIndex) {
                    lastStepContinueWaiter.await(1000)
                }
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        orchestrator.start()
        interruptionWaiter.await(1000, lastStepIndex + 1)
        orchestrator.interrupt()
        lastStepContinueWaiter.resume()

        orchestrator.state.waitTill { it is State.Terminal.Failure }

        (orchestrator.state.value as State.Terminal.Failure).let {
            it.cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
            it.cause.cause.assertIs<Orchestrator.StepInterruptedException>()
        }
        assertNull(orchestrator.result)
    }

    @Test
    fun canInterruptMultipleTimes() {
        val lastStepIndex = 4
        val interruptionWaiter = Waiter()
        val lastStepContinueWaiter = Waiter()
        val steps = (0..1000).asSequence().map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                interruptionWaiter.resume()
                if (i == lastStepIndex) {
                    lastStepContinueWaiter.await(1000)
                }
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        orchestrator.start()
        interruptionWaiter.await(1000, 5)
        orchestrator.interrupt()
        orchestrator.interrupt()
        orchestrator.interrupt()
        lastStepContinueWaiter.resume()

        orchestrator.state.waitTill { it is State.Terminal.Failure }

        (orchestrator.state.value as State.Terminal.Failure).let {
            it.cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
            it.cause.cause.assertIs<Orchestrator.StepInterruptedException>()
        }
        assertNull(orchestrator.result)
    }

    @Test
    fun canInterruptBeforeStart() {
        val steps = (0..10).asSequence().map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                throw IllegalStateException("Step should've never been executed.")
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        orchestrator.interrupt()
        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Failure }

        (orchestrator.state.value as State.Terminal.Failure).let {
            it.cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
            assertNull(it.cause.cause)
        }
        assertNull(orchestrator.result)
    }

    @Test
    fun callingStartMultipleTimesIsANoOp() {
        var callCount = 0
        val orchestrator = Orchestrator(IdentifiableString("in"), listOf<StepDescriptor<IdentifiableString>>(
                StepDescriptor(name = "step 0", maxAttempts = 1) {
                    assertEquals(0, callCount)
                    ++callCount
                    it
                }
        ).iterator(), dispatcher.createEffectiveContext())

        orchestrator.start()
        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Success }
        orchestrator.start()
    }

    @Test
    fun loggingWorks() {
        val steps = listOf<StepDescriptor<IdentifiableString>>(
                StepDescriptor(name = "step 0", maxAttempts = 1) {
                    it
                },
                StepDescriptor(name = "step 1", maxAttempts = 2) {
                    throw RuntimeException("Some error")
                }
        )

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        val logger1 = mock<Logger>()
        val logger2 = mock<Logger>()
        orchestrator.logger = logger1
        // Set the same reference again. Should not log scheduled 2x
        orchestrator.logger = logger1
        // Changed the logger. This will be logged
        orchestrator.logger = logger2

        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Failure }

        // Just scheduled
        verify(logger1, times(1)).i(any())
        verify(logger1, times(0)).d(any())
        verify(logger1, times(0)).e(any())

        // Scheduled, changed logger, attempting step 0, success step 0, attempting step 1, fail step 1, attempting step 1, fail step 1
        verify(logger2, times(7)).i(any())
        // Stack traces for 2 step 1 failures
        verify(logger2, times(2)).d(any())
        // Final failure
        verify(logger2, times(1)).e(any())
    }

    @Test
    fun `interrupt tries to interrupt the current step`() {
        val input = IdentifiableString("in")
        val result = IdentifiableString("result", input.uuid)

        val reachedInvoke = Waiter()
        val returnResult = Waiter()

        val mockStep = mock<InterruptibleStep<IdentifiableString>>()
        runBlocking {
            whenever(mockStep.invoke(any())).doAnswer {
                reachedInvoke.resume()
                returnResult.await(1000)
                result
            }
        }

        val steps = listOf(StepDescriptor(name = "step 0", maxAttempts = 1, step = mockStep))
        val orchestrator = Orchestrator(input, steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        orchestrator.start()

        reachedInvoke.await(1000)

        verify(mockStep, never()).interrupt()
        orchestrator.interrupt()
        verify(mockStep).interrupt()

        returnResult.resume()

        orchestrator.state.waitTill { it is State.Terminal.Success }
        assertEquals(result, orchestrator.result)
    }

    @Test
    fun `null result from the step is interpreted as interruption`() {
        val reachedInvoke = Waiter()
        val returnResult = Waiter()

        val mockStep = mock<InterruptibleStep<IdentifiableString>>()
        runBlocking {
            whenever(mockStep.invoke(any())).doAnswer {
                reachedInvoke.resume()
                returnResult.await(1000)
                null
            }
        }

        val step2Called = Waiter()
        val mockStep2 = mock<InterruptibleStep<IdentifiableString>>()
        runBlocking {
            whenever(mockStep2.invoke(any())).doAnswer {
                step2Called.fail("Step 2 should've never been called.")
                null
            }
        }

        val steps = listOf(
                StepDescriptor(name = "step 0", maxAttempts = 1, step = mockStep),
                StepDescriptor(name = "step 1", maxAttempts = 1, step = mockStep2)
        )
        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        orchestrator.start()

        reachedInvoke.await(1000)

        verify(mockStep, never()).interrupt()
        orchestrator.interrupt()
        verify(mockStep).interrupt()

        returnResult.resume()

        orchestrator.state.waitTill { it is State.Terminal.Failure }
        assertNull(orchestrator.result)
        (orchestrator.state.value as State.Terminal.Failure).cause.assertIs<Orchestrator.OrchestratorInterruptedException>()
        (orchestrator.state.value as State.Terminal.Failure).cause.cause.assertIs<Orchestrator.StepInterruptedException>()
    }

    @Test
    fun `interrupting a non-started orchestrator marks it as failed`() {
        val steps = listOf(
                StepDescriptor(name = "step 0", maxAttempts = 1, step = mock<InterruptibleStep<IdentifiableString>>()),
                StepDescriptor(name = "step 1", maxAttempts = 1, step = mock<InterruptibleStep<IdentifiableString>>())
        )

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext())
        var failed = false
        orchestrator.state.observe(createMockLifecycleOwner(), Observer {
            when (it) {
                is State.Terminal.Failure -> failed = true
            }
        })
        assertFalse(failed)
        orchestrator.interrupt()
        assertTrue(failed)
        assertNull(orchestrator.result)
    }

    @Test
    fun `failureListener works`() {
        val steps = listOf<StepDescriptor<IdentifiableString>>(
                StepDescriptor(name = "step 0", maxAttempts = 1) {
                    it
                },
                StepDescriptor(name = "step 1", maxAttempts = 2) {
                    throw RuntimeException("Some error")
                }
        )

        var failed = false

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator(), launchContext = dispatcher.createEffectiveContext(), failureListener = {
            failed = true
        })

        orchestrator.start()

        orchestrator.state.waitTill { it is State.Terminal.Failure }
        assertTrue(failed)
    }
}
