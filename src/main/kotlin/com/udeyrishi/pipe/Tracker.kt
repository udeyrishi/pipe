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
    : Serializable, Identifiable, Sequential, Comparable<Tracker<T>> {

    private var started: Boolean by immutableAfterSet(false)
    private var interrupted: Boolean by immutableAfterSet(false)
    private val stateChangeListeners = mutableListOf<StateChangeListener>()

    var state: State = State.Scheduled()
        private set

    var result: T? by immutableAfterSet(null)
        private set

    private val cursor = Cursor(input, steps)

    /**
     * If the StateChangeListener throws an exception, the execution will be stopped, and state will be State.Terminal.Failure.
     */
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

    private fun onStateSuccess(nextStep: String? = null): Boolean {
        val previousState = state
        state = state.onSuccess(nextStep)
        return notifyStateChangeListeners(previousState)
    }

    private fun onStateFailure(cause: Throwable): Boolean {
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

    private suspend fun runAllSteps() {
        while (true) {
            val (nextInput: T, nextStep: StepDescriptor<T>?) = cursor

            if (nextStep == null) {
                result = nextInput
                if (onStateSuccess()) {
                    // No more steps. Next input _is the_ result for this tracker
                    state.sanityCheck<State.Terminal.Success>()
                } else {
                    // Bad state change listener callback
                    state.sanityCheck<State.Terminal.Failure>()
                }
                break
            }

            val stepResult: T? = runStep(nextInput, nextStep)

            if (stepResult == null) {
                // Ran out of attempts or interrupted, or a bad state changed callback
                if (state !is State.Terminal.Failure) {
                    // Normal execution: Ran out of attempts or interrupted
                    state.sanityCheck<State.Running.AttemptFailed>()
                    onStateFailure(cause = if (interrupted) TrackerInterruptedException(this) else StepOutOfAttemptsException(this, nextStep))
                    // No need to check the result for ^. Even if it's false, the state is still terminal false
                    state.sanityCheck<State.Terminal.Failure>()
                }
                break
            }

            state.sanityCheck<State.Running.AttemptSuccessful>()

            cursor.move(nextInput = stepResult)
        }
    }

    private suspend fun <T> runStep(nextInput: T, nextStep: StepDescriptor<T>): T? {
        val (name: String, maxAttempts: Long, step: Step<T>) = nextStep

        for (i in 0L until maxAttempts) {
            if (!onStateSuccess(nextStep.name)) {
                // Bad state change listener
                state.sanityCheck<State.Terminal.Failure>()
                break
            }
            state.sanityCheck<State.Running.Attempting>()

            if (interrupted) {
                if (onStateFailure(StepInterruptedException(tracker = this, attempt = i, stepName = name))) {
                    state.sanityCheck<State.Running.AttemptFailed>()
                } else {
                    // Bad state change listener
                    state.sanityCheck<State.Terminal.Failure>()
                }
                break
            }

            val stepResult: T? = try {
                val result = step.doStep(nextInput)
                if (!onStateSuccess()) {
                    // Bad state change listener
                    state.sanityCheck<State.Terminal.Failure>()
                    break
                }
                result
            } catch (e: Throwable) {
                if (!onStateFailure(StepFailureException(tracker = this, attempt = i, stepName = name, throwable = e))) {
                    // Bad state change listener
                    state.sanityCheck<State.Terminal.Failure>()
                    break
                }
                null
            }

            if (stepResult == null) {
                state.sanityCheck<State.Running.AttemptFailed>()
            } else {
                state.sanityCheck<State.Running.AttemptSuccessful>()
                return stepResult
            }
        }

        return null
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