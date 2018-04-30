/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package come.udeyrishi.pipe

import com.udeyrishi.pipe.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StateTest {

    @Test
    fun scheduledWorks() {
        val scheduled = State.Scheduled()
        assertTrue(scheduled.onSuccess() is State.Terminal.Success)

        val cause = RuntimeException("Some error")

        val terminalFailure: State.Terminal.Failure = scheduled.onFailure(cause)
        assertEquals(1, terminalFailure.causes.size)
        assertTrue(cause === terminalFailure.causes[0])

        val running = scheduled.onSuccess(nextStep = "foo")
        assertTrue(running is State.Running.Attempting)
        assertEquals("foo", (running as State.Running.Attempting).step)

        assertEquals("Scheduled", scheduled.toString())
    }

    @Test
    fun runningStepWorks() {
        val runningStep = State.Running.Attempting("foo")
        assertEquals("Attempting(step=foo)", runningStep.toString())

        val cause = RuntimeException("Some error")
        val attemptFailed: State.Running.AttemptFailed = runningStep.onFailure(cause)
        assertEquals(1, attemptFailed.causes.size)
        assertEquals(cause, attemptFailed.causes[0])

        assertTrue(runningStep.onSuccess() is State.Terminal.Success)

        val nextRunningStep = runningStep.onSuccess("bar")
        assertTrue(nextRunningStep is State.Running.Attempting)

        assertEquals("bar", (nextRunningStep as State.Running.Attempting).step)
    }

    @Test
    fun stepFailedWorks() {
        val cause = RuntimeException("Some error")
        val attemptFailed: State.Running.AttemptFailed = State.Running.AttemptFailed(cause)
        assertEquals(1, attemptFailed.causes.size)
        assertEquals(cause, attemptFailed.causes[0])

        val cause2 = RuntimeException("Some error 2")
        val terminal: State.Terminal.Failure = attemptFailed.onFailure(cause2)
        assertEquals(2, terminal.causes.size)
        assertEquals(cause, terminal.causes[0])
        assertEquals(cause2, terminal.causes[1])

        val success: State.Running.Attempting = attemptFailed.onSuccess("foo")
        assertEquals("foo", success.step)
    }

    @Test
    fun terminalSuccessWorks() {
        val terminalSuccess = State.Terminal.Success()
        assertEquals("Success", terminalSuccess.toString())
    }

    @Test
    fun terminalFailureWorks() {
        val causes = listOf(RuntimeException("foo"), RuntimeException("bar"))
        val terminalFailure = State.Terminal.Failure(causes = causes)
        assertEquals(2, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])
        assertEquals("Failure(causes=2)", terminalFailure.toString())
    }
}