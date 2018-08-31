/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.udeyrishi.pipe.steps.StepDescriptor
import com.udeyrishi.pipe.util.Identifiable
import com.udeyrishi.pipe.util.immutableAfterSet
import kotlinx.coroutines.experimental.launch
import java.util.UUID

/**
 * An object that:
 * - guides the provided input through the provided steps
 * - updates and monitors its state
 */
internal class Orchestrator<out T : Identifiable>(input: T, steps: Iterator<StepDescriptor<T>>) : Identifiable {
    override val uuid: UUID = input.uuid

    private var started: Boolean by immutableAfterSet(false)
    private var interrupted: Boolean by immutableAfterSet(false)

    // Orchestrator must read/write to volatileState.
    // Posting to _state directly is non-dependable, since the post is asynchronous.
    private var volatileState: State = State.Scheduled
        set(value) {
            field = value
            _state.postValue(value)
        }

    private val _state = MutableLiveData<State>()
    val state: LiveData<State>
        get() = _state

    private var _result: T? by immutableAfterSet(null)
    val result: T?
        get() = _result

    private val cursor = Cursor(input, steps)

    init {
        _state.postValue(volatileState)
    }

    fun start() {
        if (started) {
            throw IllegalStateException("Cannot start a ${this.javaClass.simpleName} twice.")
        }

        started = true

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
        if (interrupted) {
            volatileState.sanityCheck<State.Scheduled>()
            onStateFailure(cause = OrchestratorInterruptedException(this))
            volatileState.sanityCheck<State.Terminal.Failure>()
            return
        }

        while (true) {
            val (input, nextStep) = cursor

            if (nextStep == null) {
                // No more steps. Next input _is the_ result for this orchestrator
                onResultPrepared(result = input)
                break
            }

            val stepResult = runStep(input, nextStep)

            if (stepResult.stepResult == null) {
                onStepResultNull(failingStep = nextStep, dueToInterruption = stepResult.interrupted)
                break
            }

            volatileState.sanityCheck<State.Running.AttemptSuccessful>()
            cursor.move(nextInput = stepResult.stepResult)
        }
    }

    private fun onResultPrepared(result: T) {
        this._result = result
        onStateSuccess()
        volatileState.sanityCheck<State.Terminal.Success>()
    }

    private fun onStepResultNull(failingStep: StepDescriptor<T>, dueToInterruption: Boolean) {
        // Ran out of attempts or interrupted, or a bad state changed callback
        if (volatileState !is State.Terminal.Failure) {
            // Normal execution: Ran out of attempts or interrupted
            volatileState.sanityCheck<State.Running.AttemptFailed>()
            onStateFailure(cause = if (dueToInterruption) OrchestratorInterruptedException(this) else StepOutOfAttemptsException(this, failingStep))
            // No need to check the result for ^. Even if it's false, the state is still terminal false
            volatileState.sanityCheck<State.Terminal.Failure>()
        }
    }

    private suspend fun runStep(input: T, nextStep: StepDescriptor<T>): StepResult<T> {
        for (i in 0L until nextStep.maxAttempts) {
            onStateSuccess(nextStep.name)
            volatileState.sanityCheck<State.Running.Attempting>()

            if (checkInterruption(attempt = i, stepName = nextStep.name)) {
                return StepResult(stepResult = null, interrupted = true)
            }

            val attemptResult = doStepAttempt(attempt = i, step = nextStep, input = input)
            if (attemptResult.fatalError) break

            if (attemptResult.stepResult == null) {
                volatileState.sanityCheck<State.Running.AttemptFailed>()
            } else {
                volatileState.sanityCheck<State.Running.AttemptSuccessful>()
                return StepResult(stepResult = attemptResult.stepResult, interrupted = false)
            }
        }

        return StepResult(stepResult = null, interrupted = false)
    }

    private fun checkInterruption(attempt: Long, stepName: String): Boolean {
        return if (interrupted) {
            onStateFailure(StepInterruptedException(orchestrator = this, attempt = attempt, stepName = stepName))
            volatileState.sanityCheck<State.Running.AttemptFailed>()
            true
        } else {
            false
        }
    }

    private suspend fun doStepAttempt(attempt: Long, step: StepDescriptor<T>, input: T): StepAttemptResult<T> {
        return try {
            val result: T = step.step(input)
            onStateSuccess()
            StepAttemptResult.forSuccess(result)
        } catch (e: Throwable) {
            onStateFailure(StepFailureException(orchestrator = this, attempt = attempt, stepName = step.name, throwable = e))
            StepAttemptResult.forFailedStep()
        }
    }

    override fun toString() = "${this::class.java.simpleName}(uuid=$uuid)"

    private data class StepResult<out T>(val stepResult: T?, val interrupted: Boolean) {
        init {
            if (stepResult != null && interrupted) {
                throw IllegalArgumentException("Cannot provide a non-null result, and interupted = true.")
            }
        }
    }

    private fun onStateSuccess(nextStep: String? = null) {
        volatileState = volatileState.onSuccess(nextStep)
    }

    private fun onStateFailure(cause: Throwable) {
        volatileState = volatileState.onFailure(cause)
    }

    private class StepAttemptResult<out T> private constructor(val stepResult: T?, val fatalError: Boolean) {
        companion object {
            fun forFailedStep() = StepAttemptResult(null, false)
            fun <T> forSuccess(result: T) = StepAttemptResult(result, false)
        }
    }

    class OrchestratorInterruptedException internal constructor(orchestrator: Orchestrator<*>) : RuntimeException("$orchestrator prematurely interrupted.")
    class StepOutOfAttemptsException internal constructor(orchestrator: Orchestrator<*>, failureStep: StepDescriptor<*>) : RuntimeException("$orchestrator ran out of the max allowed ${failureStep.step} attempts for step '${failureStep.name}'.")
    class StepFailureException internal constructor(orchestrator: Orchestrator<*>, attempt: Long, stepName: String, throwable: Throwable) : RuntimeException("$orchestrator failed on step '$stepName''s attempt #$attempt.", throwable)
    class StepInterruptedException internal constructor(orchestrator: Orchestrator<*>, attempt: Long, stepName: String) : RuntimeException("$orchestrator was interrupted at step '$stepName' on the attempt #$attempt.")
}

private inline fun <reified T : State> State.sanityCheck() {
    if (this !is T) {
        throw IllegalStateException("Something went wrong. State must've been ${T::class.java.simpleName}, but was ${this::class.java.simpleName}.")
    }
}

private class Cursor<T : Identifiable>(private var input: T, private val steps: Iterator<StepDescriptor<T>>) {
    var nextStep: StepDescriptor<T>? = if (steps.hasNext()) steps.next() else null
        private set

    fun move(nextInput: T) {
        if (nextInput.uuid != this.input.uuid) {
            throw IllegalArgumentException("The unique identifiers of the inputs and outputs must stay the same.")
        }
        this.input = nextInput
        nextStep = if (steps.hasNext()) steps.next() else null
    }

    operator fun component1() = input
    operator fun component2() = nextStep
}
