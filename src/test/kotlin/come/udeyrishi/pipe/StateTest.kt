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

        val running = scheduled.onSuccess(nextStep = "foo")
        assertTrue(running is State.Running.Attempting)
        assertEquals("foo", (running as State.Running.Attempting).step)

        assertEquals("Scheduled", scheduled.toString())
    }

    @Test(expected = IllegalStateException::class)
    fun scheduledFailsOnFailure() {
        val scheduled = State.Scheduled()
        val cause = RuntimeException("Some error")
        scheduled.onFailure(cause)
    }

    @Test
    fun runningStepWorks() {
        val runningStep = State.Running.Attempting("foo")
        assertEquals("Attempting(step=foo)", runningStep.toString())

        val cause = RuntimeException("Some error")
        val attemptFailed = runningStep.onFailure(cause)
        assertTrue(attemptFailed is State.Running.AttemptFailed)

        (attemptFailed as State.Running.AttemptFailed).let {
            assertEquals(cause, it.cause)
        }

        assertTrue(runningStep.onSuccess() is State.Running.AttemptSuccessful)
    }

    @Test(expected = IllegalArgumentException::class)
    fun runningStepStateFailsOnSuccessWithNextStepName() {
        val runningStep = State.Running.Attempting("foo")
        runningStep.onSuccess("bar")
    }

    @Test
    fun stepSucceededWorks() {
        val stepSucceeded = State.Running.AttemptSuccessful("foo")
        assertEquals("AttemptSuccessful(step=foo)", stepSucceeded.toString())
        assertTrue(stepSucceeded.onSuccess() is State.Terminal.Success)

        val attempting = stepSucceeded.onSuccess("bar")
        assertTrue(attempting is State.Running.Attempting)
        assertEquals("bar", (attempting as State.Running.Attempting).step)
    }

    @Test(expected = IllegalStateException::class)
    fun stepSucceededFailsOnFailure() {
        val stepSucceeded = State.Running.AttemptSuccessful("foo")
        stepSucceeded.onFailure(RuntimeException())
    }

    @Test
    fun stepFailedWorks() {
        val cause = RuntimeException("Some error")
        val attemptFailed: State.Running.AttemptFailed = State.Running.AttemptFailed("foo", cause)
        assertEquals(cause, attemptFailed.cause)
        assertEquals("foo", attemptFailed.step)

        val cause2 = RuntimeException("Some error 2")
        val terminal = attemptFailed.onFailure(cause2)
        assertTrue(terminal is State.Terminal.Failure)

        (terminal as State.Terminal.Failure).let {
            assertEquals(2, it.causes.size)
            assertEquals(cause, it.causes[0])
            assertEquals(cause2, it.causes[1])
        }

        val success = attemptFailed.onSuccess()
        assertTrue(success is State.Running.Attempting)
        assertEquals("foo", (success as State.Running.Attempting).step)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failureStepStateFailsOnSuccessWithNextStepName() {
        val runningStep = State.Running.AttemptFailed("foo", RuntimeException())
        runningStep.onSuccess("bar")
    }

    @Test
    fun terminalSuccessWorks() {
        val terminalSuccess = State.Terminal.Success()
        assertEquals("Success", terminalSuccess.toString())
        assertTrue(terminalSuccess === terminalSuccess.onSuccess())
    }

    @Test(expected = IllegalStateException::class)
    fun terminalSuccessStateFailsOnFailure() {
        State.Terminal.Success().onFailure(RuntimeException())
    }

    @Test(expected = IllegalArgumentException::class)
    fun terminalSuccessStateFailsOnSuccessWithNextStepName() {
        val terminalSuccess = State.Terminal.Success()
        terminalSuccess.onSuccess("bar")
    }

    @Test
    fun terminalFailureWorks() {
        val causes = listOf(RuntimeException("foo"), RuntimeException("bar"))
        val terminalFailure = State.Terminal.Failure(causes = causes)
        assertEquals(2, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])
        assertEquals("Failure(causes=2)", terminalFailure.toString())

        val anotherCause = RuntimeException("hello")
        val anotherRef = terminalFailure.onFailure(anotherCause)

        assertTrue(anotherRef === terminalFailure)

        assertEquals(3, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])
        assertEquals(anotherCause, terminalFailure.causes[2])
        assertEquals("Failure(causes=3)", terminalFailure.toString())
    }

    @Test(expected = IllegalStateException::class)
    fun terminalFailureFailsOnSuccess() {
        State.Terminal.Failure(listOf(RuntimeException())).onSuccess()
    }
}