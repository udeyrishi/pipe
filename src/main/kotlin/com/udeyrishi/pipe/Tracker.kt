/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.util.immutableAfterSet
import com.udeyrishi.pipe.util.synchronizedRun
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import java.io.Serializable
import java.util.UUID

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class Tracker<T : Serializable> internal constructor(override val uuid: UUID, override val position: Long, input: T, steps: Iterator<StepDescriptor<T>>)
    : Identifiable, Sequential, Comparable<Tracker<T>> {

    private var started: Boolean by immutableAfterSet(false)
    private var interrupted: Boolean by immutableAfterSet(false)
    private val stateHolder = StateHolder()

    var result: T? by immutableAfterSet(null)
        private set

    val state: State
        get() = stateHolder.state

    private val cursor = Cursor(input, steps)

    /**
     * If the StateChangeListener throws an exception, the execution will be stopped, and state will be State.Terminal.Failure.
     */
    fun subscribe(stateChangeListener: StateChangeListener) = stateHolder.subscribe(stateChangeListener)
    fun unsubscribe(stateChangeListener: StateChangeListener): Boolean = stateHolder.unsubscribe(stateChangeListener)
    fun unsubscribeAll() = stateHolder.unsubscribeAll()

    fun start(stateChangeListener: StateChangeListener? = null) {
        if (started) {
            throw IllegalStateException("Cannot start a ${this.javaClass.simpleName} twice.")
        }

        started = true

        stateChangeListener?.let {
            subscribe(it)
        }

        launch {
            runAllSteps()
        }
    }

    fun interrupt() {
        if (!interrupted) {
            interrupted = true
        }
    }

    private suspend fun runAllSteps() {
        while (true) {
            val (nextInput: T, nextStep: StepDescriptor<T>?) = cursor

            if (nextStep == null) {
                onResultPrepared(result = nextInput)
                break
            }

            val stepResult: T? = runStep(nextInput, nextStep)

            if (stepResult == null) {
                onStepResultNull(failingStep = nextStep)
                break
            }

            state.sanityCheck<State.Running.AttemptSuccessful>()
            cursor.move(nextInput = stepResult)
        }
    }

    private fun onResultPrepared(result: T) {
        this.result = result
        if (stateHolder.onStateSuccess()) {
            // No more steps. Next input _is the_ result for this tracker
            state.sanityCheck<State.Terminal.Success>()
        } else {
            // Bad state change listener callback
            state.sanityCheck<State.Terminal.Failure>()
        }
    }

    private fun onStepResultNull(failingStep: StepDescriptor<T>) {
        // Ran out of attempts or interrupted, or a bad state changed callback
        if (state !is State.Terminal.Failure) {
            // Normal execution: Ran out of attempts or interrupted
            state.sanityCheck<State.Running.AttemptFailed>()
            stateHolder.onStateFailure(cause = if (interrupted) TrackerInterruptedException(this) else StepOutOfAttemptsException(this, failingStep))
            // No need to check the result for ^. Even if it's false, the state is still terminal false
            state.sanityCheck<State.Terminal.Failure>()
        }
    }

    private suspend fun runStep(nextInput: T, nextStep: StepDescriptor<T>): T? {
        for (i in 0L until nextStep.maxAttempts) {
            if (!stateHolder.onStateSuccess(nextStep.name)) {
                // Bad state change listener
                state.sanityCheck<State.Terminal.Failure>()
                break
            }
            state.sanityCheck<State.Running.Attempting>()

            if (checkInterruption(attempt = i, stepName = nextStep.name)) break

            val attemptResult = doStepAttempt(attempt = i, step = nextStep, input = nextInput)
            if (attemptResult.fatalError) break

            if (attemptResult.stepResult == null) {
                state.sanityCheck<State.Running.AttemptFailed>()
            } else {
                state.sanityCheck<State.Running.AttemptSuccessful>()
                return attemptResult.stepResult
            }
        }

        return null
    }

    private fun checkInterruption(attempt: Long, stepName: String): Boolean {
        return if (interrupted) {
            if (stateHolder.onStateFailure(StepInterruptedException(tracker = this, attempt = attempt, stepName = stepName))) {
                state.sanityCheck<State.Running.AttemptFailed>()
            } else {
                // Bad state change listener
                state.sanityCheck<State.Terminal.Failure>()
            }
            true
        } else {
            false
        }
    }

    private fun doStepAttempt(attempt: Long, step: StepDescriptor<T>, input: T): StepAttemptResult<T> {
        return try {
            val result: T = step.step.doStep(input)
            if (stateHolder.onStateSuccess()) {
                StepAttemptResult.forSuccess(result)
            } else {
                // Bad state change listener
                state.sanityCheck<State.Terminal.Failure>()
                StepAttemptResult.forFatalError()
            }
        } catch (e: Throwable) {
            if (stateHolder.onStateFailure(StepFailureException(tracker = this, attempt = attempt, stepName = step.name, throwable = e))) {
                StepAttemptResult.forFailedStep()
            } else {
                // Bad state change listener
                state.sanityCheck<State.Terminal.Failure>()
                StepAttemptResult.forFatalError()
            }
        }
    }

    private class StepAttemptResult<out T> private constructor(val stepResult: T?, val fatalError: Boolean) {
        companion object {
            fun forFatalError() = StepAttemptResult(null, true)
            fun forFailedStep() = StepAttemptResult(null, false)
            fun <T> forSuccess(result: T) = StepAttemptResult(result, false)
        }
    }

    override fun compareTo(other: Tracker<T>): Int = position.compareTo(other.position)
    override fun toString() = "${this::class.java.simpleName}(uuid=$uuid)"

    class TrackerInterruptedException internal constructor(tracker: Tracker<*>) : RuntimeException("$tracker prematurely interrupted.")
    class StepOutOfAttemptsException internal constructor(tracker: Tracker<*>, failureStep: StepDescriptor<*>) : RuntimeException("$tracker ran out of the max allowed ${failureStep.step} attempts for step '${failureStep.name}'.")
    class StepFailureException internal constructor(tracker: Tracker<*>, attempt: Long, stepName: String, throwable: Throwable) : RuntimeException("$tracker failed on step '$stepName''s attempt #$attempt.", throwable)
    class StepInterruptedException internal constructor(tracker: Tracker<*>, attempt: Long, stepName: String) : RuntimeException("$tracker was interrupted at step '$stepName' on the attempt #$attempt.")
}

private inline fun <reified T : State> State.sanityCheck() {
    if (this !is T) {
        throw IllegalStateException("Something went wrong. State must've been ${T::class.java.simpleName}, but was ${this::class.java.simpleName}.")
    }
}

private class Cursor<T>(input: T, private val steps: Iterator<StepDescriptor<T>>) {
    var nextInput: T = input
        private set
    var nextStep: StepDescriptor<T>? = if (steps.hasNext()) steps.next() else null
        private set

    fun move(nextInput: T) {
        this.nextInput = nextInput
        nextStep = if (steps.hasNext()) steps.next() else null
    }

    operator fun component1() = nextInput
    operator fun component2() = nextStep
}

private class StateHolder {
    var state: State = State.Scheduled()
        private set

    private val stateChangeListeners = mutableListOf<StateChangeListener>()

    fun subscribe(stateChangeListener: StateChangeListener) {
        stateChangeListeners.synchronizedRun {
            add(stateChangeListener)
        }
    }

    fun unsubscribe(stateChangeListener: StateChangeListener): Boolean {
        return stateChangeListeners.synchronizedRun {
            remove(stateChangeListener)
        }
    }

    fun unsubscribeAll() {
        stateChangeListeners.synchronizedRun {
            clear()
        }
    }

    fun onStateSuccess(nextStep: String? = null): Boolean {
        val previousState = state
        state = state.onSuccess(nextStep)
        return notifyStateChangeListeners(previousState)
    }

    fun onStateFailure(cause: Throwable): Boolean {
        val previousState = state
        state = state.onFailure(cause)
        return notifyStateChangeListeners(previousState)
    }

    private fun notifyStateChangeListeners(previousState: State): Boolean {
        return stateChangeListeners.synchronizedRun {
            var allCallbacksCalled = true
            for (it in this) {
                try {
                    it.onStateChanged(previousState, state)
                } catch (e: Throwable) {
                    // Bad state change listener callback. Try to stop executing, and move the state to failure
                    state = state.onFailure(e)
                    if (state !is State.Terminal.Failure) {
                        state = state.onFailure(e)
                    }
                    if (state !is State.Terminal.Failure) {
                        // Terminal Failure should not be more than 2 steps away. No clue what happened
                        throw e
                    }
                    allCallbacksCalled = false
                    break
                }
            }
            allCallbacksCalled
        }
    }
}