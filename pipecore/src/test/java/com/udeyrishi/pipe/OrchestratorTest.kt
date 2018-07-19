/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.steps.StepDescriptor
import com.udeyrishi.pipe.testutil.Repeat
import com.udeyrishi.pipe.testutil.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class OrchestratorTest {
    
    private data class IdentifiableString(val data: String, override val uuid: UUID = UUID.randomUUID()) : Identifiable
    
    @Rule
    @JvmField
    val repeatRule = RepeatRule()

    @Test
    fun startsWithScheduledState() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator())
        assertTrue(orchestrator.state is State.Scheduled)
    }

    @Test
    fun goesToCompletionWhenNoSteps() {
        val input = IdentifiableString("some input")
        val orchestrator = Orchestrator(input, listOf<StepDescriptor<IdentifiableString>>().iterator())
        assertNull(orchestrator.result)
        orchestrator.start()

        var counter = 0
        while (orchestrator.state !is State.Terminal && counter++ < 10) {
            assertNull(orchestrator.result)
            Thread.sleep(10)
        }

        assertTrue(orchestrator.state is State.Terminal.Success)
        assertEquals(input, orchestrator.result)
    }

    @Test
    fun executesStepsWithCorrectStateChanges() {
        val steps =  (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                IdentifiableString("${it.data}->$i", it.uuid)
            }
        }


        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.subscribe(StateChangeListener { _, previousState, newState ->
            when (i++) {
                0 -> {
                    // step 0 start
                    assertTrue(previousState is State.Scheduled)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                1 -> {
                    // step 0 done
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (previousState as State.Running).step)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2, 4, 6, 8, 10 -> {
                    // steps 1-5 start
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step${((i-1)/2)-1}", (previousState as State.Running).step)
                    assertEquals("step${(i-1)/2}", (newState as State.Running).step)
                }
                3, 5, 7, 9, 11 -> {
                    // steps 1-5 end
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step${(i-2)/2}", (previousState as State.Running).step)
                    assertEquals("step${(i-2)/2}", (newState as State.Running).step)
                }
                12 -> {
                    // pipeline completion
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Terminal.Success)
                    assertEquals("step5", (previousState as State.Running).step)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Success)
        assertEquals(IdentifiableString("in->0->1->2->3->4->5", input.uuid), orchestrator.result)
    }

    @Test
    fun handlesExceptionsInStateChangeListenersWhenStateIsNotAttemptFailed() {
        val steps =  (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1, {
                Thread.sleep(100)
                IdentifiableString("${it.data}->$i", it.uuid)
            })
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        val error = IllegalAccessException("testing")
        orchestrator.subscribe(StateChangeListener { _, _, _ ->
            when (i++) {
                5 -> throw error
            }
        })
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Failure)
        assertNull(orchestrator.result)
        assertEquals(1, (orchestrator.state as State.Terminal.Failure).causes.size)
        assertTrue(error === (orchestrator.state as State.Terminal.Failure).causes[0])
    }

    @Test
    fun handlesExceptionsInStateChangeListenersWhenStateIsAttemptFailed() {
        val error = IllegalAccessException("testing")
        val error2 = IllegalAccessException("testing")

        val steps =  (0..5).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                throw error
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        assertNull(orchestrator.result)

        var i = 0
        orchestrator.subscribe(StateChangeListener { _, previousState, newState ->
            when (i++) {
                1 -> {
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptFailed)
                    throw error2
                }
            }
        })
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Failure)
        assertNull(orchestrator.result)
        assertEquals(2, (orchestrator.state as State.Terminal.Failure).causes.size)
        assertTrue((orchestrator.state as State.Terminal.Failure).causes[0] is Orchestrator.StepFailureException)
        assertTrue(error === (orchestrator.state as State.Terminal.Failure).causes[0].cause)
        assertTrue(error2 === (orchestrator.state as State.Terminal.Failure).causes[1])
    }

    @Test
    fun handlesFailuresCorrectly() {
        val error = RuntimeException("something went wrong")
        var called = 0
        val steps =  (0..5).map { i ->
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
        orchestrator.subscribe(StateChangeListener { _, previousState, newState ->
            when (i++) {
                0 -> {
                    // step 0 start
                    assertTrue(previousState is State.Scheduled)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                1 -> {
                    // step 0 done
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (previousState as State.Running).step)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2, 4 -> {
                    // step starts
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${((j-1)/2)-1}", (previousState as State.Running).step)
                    assertEquals("step${(j-1)/2}", (newState as State.Running).step)
                }
                3 -> {
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j-2)/2}", (previousState as State.Running).step)
                    assertEquals("step${(j-2)/2}", (newState as State.Running).step)
                }
                5 -> {
                    // step 2 failure
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (previousState as State.Running).step)
                    assertEquals("step2", (newState as State.Running).step)
                }
                6 -> {
                    // step 2 retry
                    assertTrue(previousState is State.Running.AttemptFailed)
                    assertTrue(newState is State.Terminal.Failure)
                    assertEquals("step2", (previousState as State.Running).step)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Failure)
        assertNull(orchestrator.result)

        (orchestrator.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Orchestrator.StepFailureException)
            assertTrue((it.causes[0] as Orchestrator.StepFailureException).cause === error)
            assertTrue(it.causes[1] is Orchestrator.StepOutOfAttemptsException)
        }
    }

    @Test
    fun retriesWhenNeeded() {
        val error = RuntimeException("something went wrong")
        var failureCount = 0
        var called = 0
        val steps =  (0..5).map { i ->
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
        orchestrator.subscribe(StateChangeListener { _, previousState, newState ->
            when (i++) {
                0 -> {
                    // step 0 start
                    assertTrue(previousState is State.Scheduled)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                1 -> {
                    // step 0 done
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (previousState as State.Running).step)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2, 4, 8, 10, 12 -> {
                    // steps; starts. Skips 6 because of the failures.
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${((j-1)/2)-1}", (previousState as State.Running).step)
                    assertEquals("step${(j-1)/2}", (newState as State.Running).step)
                }
                5 -> {
                    // step 2 failure
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (previousState as State.Running).step)
                    assertEquals("step2", (newState as State.Running).step)
                    assertTrue((newState as State.Running.AttemptFailed).cause is Orchestrator.StepFailureException)
                    assertTrue((newState.cause as Orchestrator.StepFailureException).cause === error)
                }
                6 -> {
                    // step 2 retry
                    assertTrue(previousState is State.Running.AttemptFailed)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step2", (previousState as State.Running).step)
                    assertEquals("step2", (newState as State.Running).step)
                }
                3, 7, 9, 11, 13 -> {
                    // step 1, 3, 4, and 5 were successful the first time. 2 took a retry.
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j-2)/2}", (previousState as State.Running).step)
                    assertEquals("step${(j-2)/2}", (newState as State.Running).step)
                }
                14 -> {
                    // pipeline completion
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Terminal.Success)
                    assertEquals("step5", (previousState as State.Running).step)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Success)
        assertEquals(IdentifiableString("in->0->1->2->3->4->5", input.uuid), orchestrator.result)
    }

    @Test
    fun respectsMaxAttempts() {
        val error = RuntimeException("something went wrong")
        var step2Called = 0

        val steps =  (0..5).map { i ->
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
        orchestrator.subscribe(StateChangeListener { _, previousState, newState ->
            when (i++) {
                0 -> {
                    // step 0 start
                    assertTrue(previousState is State.Scheduled)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step0", (newState as State.Running).step)
                }
                1 -> {
                    // step 0 done
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    assertEquals("step0", (previousState as State.Running).step)
                    assertEquals("step0", (newState as State.Running).step)
                }
                2, 4 -> {
                    // step starts
                    assertTrue(previousState is State.Running.AttemptSuccessful)
                    assertTrue(newState is State.Running.Attempting)
                    val j = if (i < 8) i else (i - 2)
                    assertEquals("step${((j-1)/2)-1}", (previousState as State.Running).step)
                    assertEquals("step${(j-1)/2}", (newState as State.Running).step)
                }
                3 -> {
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptSuccessful)
                    val j = if (i < 7) i else (i - 2)
                    assertEquals("step${(j-2)/2}", (previousState as State.Running).step)
                    assertEquals("step${(j-2)/2}", (newState as State.Running).step)
                }
                5, 7, 9, 11, 13 -> {
                    // step 2 failure
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptFailed)
                    assertEquals("step2", (previousState as State.Running).step)
                    assertEquals("step2", (newState as State.Running).step)
                }
                6, 8, 10, 12 -> {
                    // step 2 retry
                    assertTrue(previousState is State.Running.AttemptFailed)
                    assertTrue(newState is State.Running.Attempting)
                    assertEquals("step2", (previousState as State.Running).step)
                    assertEquals("step2", (newState as State.Running).step)
                }
                14 -> {
                    assertTrue(previousState is State.Running.AttemptFailed)
                    assertTrue(newState is State.Terminal.Failure)
                    assertEquals("step2", (previousState as State.Running).step)
                }
                else -> fail("Counter should've never reached $i.")
            }
        })

        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Failure)
        assertNull(orchestrator.result)

        (orchestrator.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Orchestrator.StepFailureException)
            assertTrue((it.causes[0] as Orchestrator.StepFailureException).cause === error)
            assertTrue(it.causes[1] is Orchestrator.StepOutOfAttemptsException)
        }

        assertEquals(5, step2Called)
    }

    @Test
    @Repeat
    fun interruptsCorrectly() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
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

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, orchestrator should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(orchestrator.state is State.Terminal.Failure)

        (orchestrator.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Orchestrator.StepInterruptedException)
            assertTrue(it.causes[1] is Orchestrator.OrchestratorInterruptedException)
        }
        assertNull(orchestrator.result)
    }

    @Test
    @Repeat
    fun canInterruptMultipleTimes() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
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

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, orchestrator should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(orchestrator.state is State.Terminal.Failure)

        (orchestrator.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Orchestrator.StepInterruptedException)
            assertTrue(it.causes[1] is Orchestrator.OrchestratorInterruptedException)
        }
        assertNull(orchestrator.result)
    }

    @Test
    @Repeat
    fun canInterruptBeforeStart() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                Thread.sleep(100)
                lastStep = i
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        orchestrator.interrupt()
        orchestrator.start()
        orchestrator.interrupt()
        Thread.sleep(500)

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertEquals(-1, lastStep)
        assertTrue(orchestrator.state is State.Terminal.Failure)

        (orchestrator.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Orchestrator.StepInterruptedException)
            assertTrue(it.causes[1] is Orchestrator.OrchestratorInterruptedException)
        }
        assertNull(orchestrator.result)
    }

    @Test
    fun callsStateChangeListeners() {
        val steps =  (0..2).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                it
            }
        }

        var callbackCount = 0
        val listener = StateChangeListener { _, _, _ ->
            ++callbackCount
        }

        val input = IdentifiableString("in")
        val orchestrator = Orchestrator(input, steps.iterator())
        orchestrator.subscribe(listener)
        orchestrator.subscribe(listener)
        orchestrator.subscribe(listener)
        orchestrator.subscribe(listener)
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // For every step, a `-> running` and `-> done` transition = step.size * 2
        // +1 for the final completion.
        // * 4, because 4 listeners
        assertTrue(orchestrator.state is State.Terminal.Success)
        assertEquals(IdentifiableString("in", input.uuid), orchestrator.result)
        assertEquals((steps.size*2 + 1) * 4, callbackCount)
    }

    @Test(expected = IllegalStateException::class)
    fun cannotStartMultipleTimes() {
        val orchestrator = Orchestrator(IdentifiableString("in"), listOf<StepDescriptor<IdentifiableString>>().iterator())
        orchestrator.start()
        orchestrator.start()
    }

    @Test
    fun unsubscribeWorks() {
        val steps =  (0..2).map { i ->
            StepDescriptor<IdentifiableString>("step$i", 1) {
                it
            }
        }

        val listener = StateChangeListener { _, _, _ ->
        }

        var called = false
        val badListener = StateChangeListener { _, _, _ ->
            called = true
            fail("should not have been called")
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())
        orchestrator.subscribe(listener)
        orchestrator.subscribe(listener)
        orchestrator.subscribe(badListener)
        orchestrator.subscribe(listener)
        assertTrue(orchestrator.unsubscribe(badListener))
        assertFalse(orchestrator.unsubscribe(StateChangeListener { _, _, _ ->  }))
        orchestrator.subscribe(listener)
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Success)
        assertFalse(called)
    }

    @Test
    fun callbacksAreCalledInTheCorrectOrder() {
        var i = 0
        val listener1 = StateChangeListener { _, _, _ ->
            assertEquals(0, i)
            ++i
        }

        val listener2 = StateChangeListener { _, _, _ ->
            assertEquals(1, i)
            ++i
        }

        val listener3 = StateChangeListener { _, _, _ ->
            assertEquals(2, i)
            i = (i + 1) % 3
        }

        val steps =  (0..2).map { j ->
            StepDescriptor<IdentifiableString>("step$j", 1) {
                it
            }
        }

        val orchestrator = Orchestrator(IdentifiableString("in"), steps.iterator())

        orchestrator.subscribe(listener1)
        orchestrator.subscribe(listener2)
        orchestrator.subscribe(listener3)
        orchestrator.start()

        while (orchestrator.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(orchestrator.state is State.Terminal.Success)
        assertEquals(0, i)
    }
}