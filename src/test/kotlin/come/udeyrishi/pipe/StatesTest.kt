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
class StatesTest {

    @Test
    fun scheduledWorks() {
        val scheduled = State.Scheduled()
        assertTrue(scheduled.onSuccess() is State.TerminalSuccess)

        val cause = RuntimeException("Some error")

        val terminalFailure: State.TerminalFailure = scheduled.onFailure(cause)
        assertEquals(1, terminalFailure.causes.size)
        assertTrue(cause === terminalFailure.causes[0])

        val running = scheduled.onSuccess(nextStep = "foo")
        assertTrue(running is State.RunningStep)
        assertEquals("foo", (running as State.RunningStep).step)

        assertEquals("Scheduled", scheduled.toString())
    }

    @Test
    fun runningStepWorks() {
        val runningStep = State.RunningStep("foo")
        assertEquals("RunningStep(step=foo)", runningStep.toString())

        val cause = RuntimeException("Some error")
        val stepFailed: State.StepFailed = runningStep.onFailure(cause)
        assertEquals(1, stepFailed.causes.size)
        assertEquals(cause, stepFailed.causes[0])

        assertTrue(runningStep.onSuccess() is State.TerminalSuccess)

        val nextRunningStep = runningStep.onSuccess("bar")
        assertTrue(nextRunningStep is State.RunningStep)

        assertEquals("bar", (nextRunningStep as State.RunningStep).step)
    }

    @Test
    fun stepFailedWorks() {
        val cause = RuntimeException("Some error")
        val stepFailed: State.StepFailed = State.StepFailed(cause)
        assertEquals(1, stepFailed.causes.size)
        assertEquals(cause, stepFailed.causes[0])

        val cause2 = RuntimeException("Some error 2")
        val terminal: State.TerminalFailure = stepFailed.onFailure(cause2)
        assertEquals(2, terminal.causes.size)
        assertEquals(cause, terminal.causes[0])
        assertEquals(cause2, terminal.causes[1])

        val success: State.RunningStep = stepFailed.onSuccess("foo")
        assertEquals("foo", success.step)
    }

    @Test
    fun terminalSuccessWorks() {
        val terminalSuccess = State.TerminalSuccess()
        assertEquals("TerminalSuccess", terminalSuccess.toString())
    }

    @Test
    fun terminalFailureWorks() {
        val causes = listOf(RuntimeException("foo"), RuntimeException("bar"))
        val terminalFailure = State.TerminalFailure(causes = causes)
        assertEquals(2, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])
        assertEquals("TerminalFailure(causes=2)", terminalFailure.toString())
    }
}