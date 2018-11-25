/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.testutil.assertIs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StateTest {

    @Test
    fun scheduledWorks() {
        val scheduled = State.Scheduled
        assertEquals(State.Terminal.Success, scheduled.onSuccess())

        val running = scheduled.onSuccess(nextStep = "foo")
        running.assertIs<State.Running.Attempting>()
        assertEquals("foo", (running as State.Running.Attempting).step)

        val cause = RuntimeException("Some error")
        val terminal = scheduled.onFailure(cause)
        terminal.assertIs<State.Terminal.Failure>()
        assertEquals(cause, (terminal as State.Terminal.Failure).cause)
    }

    @Test
    fun runningStepWorks() {
        val runningStep = State.Running.Attempting("foo")

        val cause = RuntimeException("Some error")
        val attemptFailed = runningStep.onFailure(cause)
        attemptFailed.assertIs<State.Running.AttemptFailed>()
        assertEquals(cause, (attemptFailed as State.Running.AttemptFailed).cause)
        runningStep.onSuccess().assertIs<State.Running.AttemptSuccessful>()
    }

    @Test(expected = IllegalArgumentException::class)
    fun runningStepStateFailsOnSuccessWithNextStepName() {
        val runningStep = State.Running.Attempting("foo")
        runningStep.onSuccess("bar")
    }

    @Test
    fun stepSucceededWorks() {
        val stepSucceeded = State.Running.AttemptSuccessful("foo")
        assertEquals(State.Terminal.Success, stepSucceeded.onSuccess())

        val attempting = stepSucceeded.onSuccess("bar")
        attempting.assertIs<State.Running.Attempting>()
        assertEquals("bar", (attempting as State.Running.Attempting).step)

        val cause = RuntimeException()
        val terminal = stepSucceeded.onFailure(cause)
        terminal.assertIs<State.Terminal.Failure>()
        assertEquals(cause, (terminal as State.Terminal.Failure).cause)
    }

    @Test
    fun stepFailedWorks() {
        val cause = RuntimeException("Some error")
        val attemptFailed: State.Running.AttemptFailed = State.Running.AttemptFailed("foo", cause)
        assertEquals(cause, attemptFailed.cause)
        assertEquals("foo", attemptFailed.step)

        val cause2 = RuntimeException("Some error 2")
        val terminal = attemptFailed.onFailure(cause2)
        terminal.assertIs<State.Terminal.Failure>()

        // It's the caller's responsibility to set up the cause-chain correctly
        assertEquals(cause2, (terminal as State.Terminal.Failure).cause)

        val success = attemptFailed.onSuccess("foo")
        success.assertIs<State.Running.Attempting>()
        assertEquals("foo", (success as State.Running.Attempting).step)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failureStepStateFailsOnSuccessWithInconsistentStepName() {
        val runningStep = State.Running.AttemptFailed("foo", RuntimeException())
        runningStep.onSuccess("bar")
    }

    @Test
    fun terminalSuccessWorks() {
        assertEquals(State.Terminal.Success, State.Terminal.Success.onSuccess())

        val cause = RuntimeException()
        val terminalFailure = State.Terminal.Success.onFailure(cause)
        terminalFailure.assertIs<State.Terminal.Failure>()
        assertEquals(cause, (terminalFailure as State.Terminal.Failure).cause)
    }

    @Test(expected = IllegalArgumentException::class)
    fun terminalSuccessStateFailsOnSuccessWithNextStepName() {
        val terminalSuccess = State.Terminal.Success
        terminalSuccess.onSuccess("bar")
    }

    @Test
    fun terminalFailureWorks() {
        val cause = RuntimeException("foo")
        val terminalFailure = State.Terminal.Failure(cause)
        assertEquals(cause, terminalFailure.cause)

        val anotherCause = RuntimeException("hello")
        val newTerminalFailure = terminalFailure.onFailure(anotherCause)

        assertNotEquals(terminalFailure, newTerminalFailure)
        assertEquals(anotherCause, (newTerminalFailure as State.Terminal.Failure).cause)
    }

    @Test(expected = IllegalStateException::class)
    fun terminalFailureFailsOnSuccess() {
        State.Terminal.Failure(RuntimeException()).onSuccess()
    }
}
