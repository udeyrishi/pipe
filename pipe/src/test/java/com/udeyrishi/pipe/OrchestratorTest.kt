/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.udeyrishi.pipe.steps.StepDescriptor
import com.udeyrishi.pipe.testutil.Repeat
import com.udeyrishi.pipe.testutil.RepeatRule
import com.udeyrishi.pipe.testutil.createMockLifecycleOwner
import com.udeyrishi.pipe.util.Identifiable
import com.udeyrishi.pipe.util.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
class OrchestratorTest {

    private data class IdentifiableString(val data: String, override val uuid: UUID = UUID.randomUUID()) : Identifiable

    @get:Rule
    val repeatRule = RepeatRule()

    @get:Rule
    val instantExecutionRule = InstantTaskExecutorRule()

    @Test
    fun startsWithScheduledState() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator())
        assertTrue(orchestrator.state.value === State.Scheduled)
    }

    @Test
    fun goesToCompletionWhenNoSteps() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator())
        assertNull(orchestrator.result)
        orchestrator.start()

        var counter = 0
        while (orchestrator.state.value !is State.Terminal && counter++ < 10) {
            Thread.sleep(10)
        }

        assertTrue(orchestrator.state.value === State.Terminal.Success)
        assertEquals(input, orchestrator.result)
    }

    @Test
    fun executesStepsWithCorrectStateChanges() {
        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                IdentifiableString("${it.data}->$i", it.uuid)
            }
        }

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertTrue(orchestrator.state.value === State.Scheduled)
                1 -> {
                    // step 0 start
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5, 7, 9, 11 -> {
                    // steps 1-5 start
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step${(i - 1) / 2}", (newState as State.Running).step)
                }
                4, 6, 8, 10, 12 -> {
                    // steps 1-5 end
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step${(i - 2) / 2}", (newState as State.Running).step)
                }
                13 -> {
                    // pipeline completion
                    assertTrue(newState === State.Terminal.Success)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state.value === State.Terminal.Success)
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
                Thread.sleep(100)
                when {
                    i == 2 -> throw error
                    i > 2 -> throw AssertionError("Step 2 should've been the last one.")
                    else -> IdentifiableString("${it.data}->$i", it.uuid)
                }
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertTrue(orchestrator.state.value === State.Scheduled)
                1 -> {
                    // step 0 start
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5 -> {
                    // step starts
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                4 -> {
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                6 -> {
                    // step 2 failure
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (newState as State.Running).step)
                }
                7 -> {
                    // step 2 retry
                    assertTrue(newState is State.Terminal.Failure)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state.value is State.Terminal.Failure)
        assertNull(orchestrator.result)

        (orchestrator.state.value as State.Terminal.Failure).let {
            assertTrue(it.cause is Orchestrator.StepOutOfAttemptsException)
            assertTrue(it.cause.cause is Orchestrator.StepFailureException)
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

                Thread.sleep(100)

                if (i == 2 && failureCount++ < 1) {
                    throw error
                }
                IdentifiableString("${it.data}->$i", it.uuid)
            }
        }

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertTrue(orchestrator.state.value === State.Scheduled)
                1 -> {
                    // step 0 start
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5, 9, 11, 13 -> {
                    // steps; starts. Skips 6 because of the failures.
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                6 -> {
                    // step 2 failure
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (newState as State.Running).step)
                    assertTrue((newState as State.Running.AttemptFailed).cause is Orchestrator.StepFailureException)
                    assertTrue((newState.cause as Orchestrator.StepFailureException).cause === error)
                }
                7 -> {
                    // step 2 retry
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step2", (newState as State.Running).step)
                }
                4, 8, 10, 12, 14 -> {
                    // step 1, 3, 4, and 5 were successful the first time. 2 took a retry.
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                15 -> {
                    // pipeline completion
                    assertTrue(newState === State.Terminal.Success)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state.value === State.Terminal.Success)
        assertEquals(IdentifiableString("in->0->1->2->3->4->5", input.uuid), orchestrator.result)
    }

    @Test
    fun respectsMaxAttempts() {
        val error = RuntimeException("something went wrong")
        var step2Called = 0

        val steps = (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 5) {
                Thread.sleep(100)
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

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.state.observe(createMockLifecycleOwner(), Observer { newState ->
            when (i++) {
                0 -> assertTrue(orchestrator.state.value === State.Scheduled)
                1 -> {
                    // step 0 start
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2 -> {
                    // step 0 done
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (newState as State.Running).step)
                }
                3, 5 -> {
                    // step starts
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${(j - 1) / 2}", (newState as State.Running).step)
                }
                4 -> {
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j - 2) / 2}", (newState as State.Running).step)
                }
                6, 8, 10, 12, 14 -> {
                    // step 2 failure
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (newState as State.Running).step)
                }
                7, 9, 11, 13 -> {
                    // step 2 retry
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step2", (newState as State.Running).step)
                }
                15 -> {
                    assertTrue(newState is State.Terminal.Failure)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state.value is State.Terminal.Failure)
        assertNull(orchestrator.result)

        (orchestrator.state.value as State.Terminal.Failure).let {
            assertTrue(it.cause is Orchestrator.StepOutOfAttemptsException)
            assertTrue(it.cause.cause is Orchestrator.StepFailureException)
            assertEquals(error, it.cause.cause?.cause)
        }

        assertEquals(5, step2Called)
    }

    @Test
    @Repeat
    fun interruptsCorrectly() {
        var lastStep = -1
        val steps = (0..100).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                lastStep = i
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        orchestrator.start()
        Thread.sleep(500)
        orchestrator.interrupt()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, orchestrator should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(orchestrator.state.value is State.Terminal.Failure)

        (orchestrator.state.value as State.Terminal.Failure).let {
            assertTrue(it.cause is Orchestrator.OrchestratorInterruptedException)
            assertTrue(it.cause.cause is Orchestrator.StepInterruptedException)
        }
        assertNull(orchestrator.result)
    }

    @Test
    @Repeat
    fun canInterruptMultipleTimes() {
        var lastStep = -1
        val steps = (0..100).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                lastStep = i
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        orchestrator.start()
        Thread.sleep(500)
        orchestrator.interrupt()
        orchestrator.interrupt()
        orchestrator.interrupt()

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, orchestrator should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(orchestrator.state.value is State.Terminal.Failure)

        (orchestrator.state.value as State.Terminal.Failure).let {
            assertTrue(it.cause is Orchestrator.OrchestratorInterruptedException)
            assertTrue(it.cause.cause is Orchestrator.StepInterruptedException)
        }
        assertNull(orchestrator.result)
    }

    @Test
    @Repeat
    fun canInterruptBeforeStart() {
        var lastStep = -1
        val steps = (0..100).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                lastStep = i
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        orchestrator.interrupt()
        orchestrator.start()
        Thread.sleep(500)

        while (orchestrator.state.value !is State.Terminal) {
            Thread.sleep(100)
        }

        assertEquals(-1, lastStep)
        assertTrue(orchestrator.state.value is State.Terminal.Failure)

        (orchestrator.state.value as State.Terminal.Failure).let {
            assertTrue(it.cause is Orchestrator.OrchestratorInterruptedException)
            assertNull(it.cause.cause)
        }
        assertNull(orchestrator.result)
    }

    @Test
    fun callingStartMultipleTimesIsANoOp() {
        var callCount = 0
        val orchestrator = Orchestrator(IdentifiableString("in"), listOf<StepDescriptor<IdentifiableString>>(
                StepDescriptor(name = "step 0", maxAttempts = 1) {
                    ++callCount
                    it
                }
        ).iterator())

        orchestrator.start()
        orchestrator.start()

        while (orchestrator.state.value !== State.Terminal.Success) {
            Thread.sleep(100)
        }

        assertEquals(1, callCount)

        orchestrator.start()
        assertEquals(1, callCount)
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
        val orchestrator = Orchestrator(input, steps.iterator())
        val logger1 = mock<Logger>()
        val logger2 = mock<Logger>()
        orchestrator.logger = logger1
        // Set the same reference again. Should not log scheduled 2x
        orchestrator.logger = logger1
        // Changed the logger. This will be logged
        orchestrator.logger = logger2

        orchestrator.start()

        while (orchestrator.state.value !is State.Terminal.Failure) {
            Thread.sleep(100)
        }

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
}
