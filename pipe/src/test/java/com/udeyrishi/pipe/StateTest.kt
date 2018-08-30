/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StateTest {

    @Test
    fun scheduledWorks() {
        val scheduled = State.Scheduled
        assertTrue(scheduled.onSuccess() is State.Terminal.Success)

        val running = scheduled.onSuccess(nextStep = "foo")
        assertTrue(running is State.Running.Attempting)
        assertEquals("foo", (running as State.Running.Attempting).step)

        val cause = RuntimeException("Some error")
        val terminal = scheduled.onFailure(cause)
        assertTrue(terminal is State.Terminal.Failure)
        assertEquals(1, (terminal as State.Terminal.Failure).causes.size)
        assertTrue(cause === terminal.causes[0])
    }

    @Test
    fun runningStepWorks() {
        val runningStep = State.Running.Attempting("foo")

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
        assertTrue(stepSucceeded.onSuccess() is State.Terminal.Success)

        val attempting = stepSucceeded.onSuccess("bar")
        assertTrue(attempting is State.Running.Attempting)
        assertEquals("bar", (attempting as State.Running.Attempting).step)

        val cause = RuntimeException()
        val terminal = stepSucceeded.onFailure(cause)
        assertTrue(terminal is State.Terminal.Failure)
        assertEquals(1, (terminal as State.Terminal.Failure).causes.size)
        assertTrue(cause === terminal.causes[0])
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

        val success = attemptFailed.onSuccess("foo")
        assertTrue(success is State.Running.Attempting)
        assertEquals("foo", (success as State.Running.Attempting).step)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failureStepStateFailsOnSuccessWithInconsistentStepName() {
        val runningStep = State.Running.AttemptFailed("foo", RuntimeException())
        runningStep.onSuccess("bar")
    }

    @Test
    fun terminalSuccessWorks() {
        val terminalSuccess = State.Terminal.Success
        assertTrue(terminalSuccess === terminalSuccess.onSuccess())

        val cause = RuntimeException()
        val terminalFailure = terminalSuccess.onFailure(cause)
        assertTrue(terminalFailure is State.Terminal.Failure)
        assertEquals(1, (terminalFailure as State.Terminal.Failure).causes.size)
        assertTrue(cause === terminalFailure.causes[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun terminalSuccessStateFailsOnSuccessWithNextStepName() {
        val terminalSuccess = State.Terminal.Success
        terminalSuccess.onSuccess("bar")
    }

    @Test
    fun terminalFailureWorks() {
        val causes = listOf(RuntimeException("foo"), RuntimeException("bar"))
        val terminalFailure = State.Terminal.Failure(causes = causes)
        assertEquals(2, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])

        val anotherCause = RuntimeException("hello")
        val anotherRef = terminalFailure.onFailure(anotherCause)

        assertTrue(anotherRef === terminalFailure)

        assertEquals(3, terminalFailure.causes.size)
        assertEquals(causes[0], terminalFailure.causes[0])
        assertEquals(causes[1], terminalFailure.causes[1])
        assertEquals(anotherCause, terminalFailure.causes[2])
    }

    @Test(expected = IllegalStateException::class)
    fun terminalFailureFailsOnSuccess() {
        State.Terminal.Failure(listOf(RuntimeException())).onSuccess()
    }
}
