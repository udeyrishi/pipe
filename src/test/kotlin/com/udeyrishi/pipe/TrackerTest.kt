/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

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

@RunWith(JUnit4::class)
class TrackerTest {

    @Rule
    @JvmField
    val repeatRule = RepeatRule()

    @Test
    fun metadataWorks() {
        val uuid = UUID.randomUUID()
        val position = 9L

        val tracker = Tracker(uuid, position, "some input", listOf<StepDescriptor<String>>().iterator())
        assertEquals(uuid, tracker.uuid)
        assertEquals(position, tracker.position)
    }

    @Test
    fun startsWithScheduledState() {
        val tracker = Tracker(UUID.randomUUID(), 0L, "some input", listOf<StepDescriptor<String>>().iterator())
        assertTrue(tracker.state is State.Scheduled)
    }

    @Test
    fun goesToCompletionWhenNoSteps() {
        val tracker = Tracker(UUID.randomUUID(), 0L, "some input", listOf<StepDescriptor<String>>().iterator())
        assertNull(tracker.result)
        tracker.start()

        var counter = 0
        while (tracker.state !is State.Terminal && counter++ < 10) {
            assertNull(tracker.result)
            Thread.sleep(10)
        }

        assertTrue(tracker.state is State.Terminal.Success)
        assertEquals("some input", tracker.result)
    }

    @Test
    fun executesStepsWithCorrectStateChanges() {
        val steps =  (0..5).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                "$it->$i"
            })
        }


        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        tracker.start(StateChangeListener { previousState, newState ->
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

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Success)
        assertEquals("in->0->1->2->3->4->5", tracker.result)
    }

    @Test
    fun handlesExceptionsInStateChangeListenersWhenStateIsNotAttemptFailed() {
        val steps =  (0..5).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                "$it->$i"
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        val error = IllegalAccessException("testing")
        tracker.start(StateChangeListener { _, _ ->
            when (i++) {
                5 -> throw error
            }
        })

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Failure)
        assertNull(tracker.result)
        assertEquals(1, (tracker.state as State.Terminal.Failure).causes.size)
        assertTrue(error === (tracker.state as State.Terminal.Failure).causes[0])
    }

    @Test
    fun handlesExceptionsInStateChangeListenersWhenStateIsAttemptFailed() {
        val error = IllegalAccessException("testing")
        val error2 = IllegalAccessException("testing")

        val steps =  (0..5).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                throw error
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        tracker.start(StateChangeListener { previousState, newState ->
            when (i++) {
                1 -> {
                    assertTrue(previousState is State.Running.Attempting)
                    assertTrue(newState is State.Running.AttemptFailed)
                    throw error2
                }
            }
        })

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Failure)
        assertNull(tracker.result)
        assertEquals(2, (tracker.state as State.Terminal.Failure).causes.size)
        assertTrue((tracker.state as State.Terminal.Failure).causes[0] is Tracker.StepFailureException)
        assertTrue(error === (tracker.state as State.Terminal.Failure).causes[0].cause)
        assertTrue(error2 === (tracker.state as State.Terminal.Failure).causes[1])
    }

    @Test
    fun handlesFailuresCorrectly() {
        val error = RuntimeException("something went wrong")
        val steps =  (0..5).map { i ->
            StepDescriptor("step$i", 1, object : Step<String> {
                private var called = false

                override fun doStep(input: String): String {
                    // Should not call this step more than once
                    assertFalse(called)
                    called = true
                    Thread.sleep(100)
                    return when {
                        i == 2 -> throw error
                        i > 2 -> throw AssertionError("Step 2 should've been the last one.")
                        else -> "$input->$i"
                    }
                }
            })
        }


        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        tracker.start(StateChangeListener { previousState, newState ->
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

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Failure)
        assertNull(tracker.result)

        (tracker.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Tracker.StepFailureException)
            assertTrue((it.causes[0] as Tracker.StepFailureException).cause === error)
            assertTrue(it.causes[1] is Tracker.StepOutOfAttemptsException)
        }
    }

    @Test
    fun retriesWhenNeeded() {
        val error = RuntimeException("something went wrong")
        var failureCount = 0
        val steps =  (0..5).map { i ->
            StepDescriptor("step$i", 2, object : Step<String> {
                private var called = 0

                override fun doStep(input: String): String {
                    if (i == 2) {
                        assertTrue(called++ < 2)
                    } else {
                        assertEquals(0, called++)
                    }

                    Thread.sleep(100)

                    if (i == 2 && failureCount++ < 1) {
                        throw error
                    }
                    return "$input->$i"
                }

            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        tracker.start(StateChangeListener { previousState, newState ->
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
                    assertTrue((newState as State.Running.AttemptFailed).cause is Tracker.StepFailureException)
                    assertTrue((newState.cause as Tracker.StepFailureException).cause === error)
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

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Success)
        assertEquals("in->0->1->2->3->4->5", tracker.result)
    }

    @Test
    fun respectsMaxAttempts() {
        val error = RuntimeException("something went wrong")
        var step2Called = 0

        val steps =  (0..5).map { i ->
            StepDescriptor("step$i", 5, Step<String> { input ->
                Thread.sleep(100)
                when {
                    i == 2 -> {
                        step2Called++
                        throw error
                    }
                    i > 2 -> throw AssertionError("Step 2 should've been the last one.")
                    else -> "$input->$i"
                }
            })
        }


        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        assertNull(tracker.result)

        var i = 0
        tracker.start(StateChangeListener { previousState, newState ->
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

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Failure)
        assertNull(tracker.result)

        (tracker.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Tracker.StepFailureException)
            assertTrue((it.causes[0] as Tracker.StepFailureException).cause === error)
            assertTrue(it.causes[1] is Tracker.StepOutOfAttemptsException)
        }

        assertEquals(5, step2Called)
    }

    @Test
    @Repeat(10)
    fun interruptsCorrectly() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                lastStep = i
                it
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        tracker.start()
        Thread.sleep(500)
        tracker.interrupt()

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, tracker should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(tracker.state is State.Terminal.Failure)

        (tracker.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Tracker.StepInterruptedException)
            assertTrue(it.causes[1] is Tracker.TrackerInterruptedException)
        }
        assertNull(tracker.result)
    }

    @Test
    @Repeat(10)
    fun canInterruptMultipleTimes() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                lastStep = i
                it
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        tracker.start()
        Thread.sleep(500)
        tracker.interrupt()
        tracker.interrupt()
        tracker.interrupt()

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // Ideally, tracker should've completed just the 5th step (index 4).
        // But this depends on the OS scheduling. So just check for a sane value
        assertTrue(lastStep < 7)
        assertTrue(tracker.state is State.Terminal.Failure)

        (tracker.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Tracker.StepInterruptedException)
            assertTrue(it.causes[1] is Tracker.TrackerInterruptedException)
        }
        assertNull(tracker.result)
    }

    @Test
    @Repeat(10)
    fun canInterruptBeforeStart() {
        var lastStep = -1
        val steps =  (0..100).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                Thread.sleep(100)
                lastStep = i
                it
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        tracker.interrupt()
        tracker.start()
        tracker.interrupt()
        Thread.sleep(500)

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertEquals(-1, lastStep)
        assertTrue(tracker.state is State.Terminal.Failure)

        (tracker.state as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertTrue(it.causes[0] is Tracker.StepInterruptedException)
            assertTrue(it.causes[1] is Tracker.TrackerInterruptedException)
        }
        assertNull(tracker.result)
    }

    @Test
    fun callsStateChangeListeners() {
        val steps =  (0..2).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                it
            })
        }

        var callbackCount = 0
        val listener = StateChangeListener { _, _ ->
            ++callbackCount
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        tracker.subscribe(listener)
        tracker.subscribe(listener)
        tracker.subscribe(listener)
        tracker.start(listener)

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // For every step, a `-> running` and `-> done` transition = step.size * 2
        // +1 for the final completion.
        // * 4, because 4 listeners
        assertTrue(tracker.state is State.Terminal.Success)
        assertEquals("in", tracker.result)
        assertEquals((steps.size*2 + 1) * 4, callbackCount)
    }

    @Test(expected = IllegalStateException::class)
    fun cannotStartMultipleTimes() {
        val tracker = Tracker(UUID.randomUUID(), 0L, "in", listOf<StepDescriptor<String>>().iterator())
        tracker.start()
        tracker.start()
    }

    @Test
    fun unsubscribeWorks() {
        val steps =  (0..2).map { i ->
            StepDescriptor<String>("step$i", 1, Step {
                it
            })
        }

        val listener = StateChangeListener { _, _ ->
        }

        var called = false
        val badListener = StateChangeListener { _, _ ->
            called = true
            fail("should not have been called")
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())
        tracker.subscribe(listener)
        tracker.subscribe(listener)
        tracker.subscribe(badListener)
        tracker.subscribe(listener)
        assertTrue(tracker.unsubscribe(badListener))
        assertFalse(tracker.unsubscribe(StateChangeListener { _, _ ->  }))
        tracker.start(listener)

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Success)
        assertFalse(called)
    }

    @Test
    fun callbacksAreCalledInTheCorrectOrder() {
        var i = 0
        val listener1 = StateChangeListener { _, _ ->
            assertEquals(0, i)
            ++i
        }

        val listener2 = StateChangeListener { _, _ ->
            assertEquals(1, i)
            ++i
        }

        val listener3 = StateChangeListener { _, _ ->
            assertEquals(2, i)
            i = (i + 1) % 3
        }

        val steps =  (0..2).map { j ->
            StepDescriptor<String>("step$j", 1, Step {
                it
            })
        }

        val tracker = Tracker(UUID.randomUUID(), 0L, "in", steps.iterator())

        tracker.subscribe(listener1)
        tracker.subscribe(listener2)
        tracker.start(listener3)

        while (tracker.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(tracker.state is State.Terminal.Success)
        assertEquals(0, i)
    }
}