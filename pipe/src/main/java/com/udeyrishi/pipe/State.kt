/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.internal.util.detailedToString

/**
 * Represents the state machine that all pipe jobs follow.
 *
 * See https://github.com/udeyrishi/pipe#state-machine for the state machine diagram.
 */
sealed class State {
    /**
     * Ticks the state to the next state, given the current one was successful.
     */
    internal abstract fun onSuccess(nextStep: String? = null): State

    /**
     * Ticks the state to the next state, given the current one failed.
     */
    internal abstract fun onFailure(cause: Throwable): State

    /**
     * Represents a job that has been scheduled, but hasn't been started yet.
     */
    object Scheduled : State() {
        /**
         * Ticks to [Running.Attempting] if [nextStep] is provided. Else,
         * treats it as the end-of-pipeline, and ticks to [Terminal.Success].
         */
        override fun onSuccess(nextStep: String?): State {
            return nextStep?.let {
                Running.Attempting(it)
            } ?: Terminal.Success
        }

        /**
         * Can be called if the job is interrupted while it was still scheduled.
         * Ticks to [Terminal.Failure].
         */
        override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)
    }

    /**
     * Represents a job that is running.
     *
     * @param step The name of the step.
     */
    sealed class Running(val step: String) : State() {

        /**
         * Represents a job that is attempting a step.
         */
        class Attempting internal constructor(step: String) : Running(step) {

            /**
             * Marks the step as successful, ticking it to [Running.AttemptSuccessful].
             *
             * @throws IllegalArgumentException if [nextStep] is null.
             */
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != null) {
                    throw IllegalArgumentException("nextStep must be null for ${this::class.java.name}.")
                }

                return AttemptSuccessful(step)
            }

            /**
             * Marks the step as failed, ticking it to [Running.AttemptFailed].
             */
            override fun onFailure(cause: Throwable): State = AttemptFailed(step, cause)

            /**
             * Returns the name of the state + the name of the step it was executing.
             */
            override fun toString(): String = "${super.toString()}(step=$step)"
        }

        /**
         * Represents a job that just failed an attempt of a step.
         *
         * @param cause The [Throwable] that caused the attempt to fail.
         */
        class AttemptFailed internal constructor(step: String, val cause: Throwable) : Running(step) {
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != step) {
                    throw IllegalArgumentException("nextStep must be the same as step for ${this::class.java.name}.")
                }

                return Attempting(step)
            }

            /**
             * Should be called when the step has ran out of attempts. Ticks to [Terminal.Failure].
             */
            override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)

            /**
             * Returns detailed description of the error that caused the failure.
             */
            override fun toString(): String = "${super.toString()}(cause=\n${cause.detailedToString()})"
        }

        /**
         * Represents a job that just successfully completed a step.
         */
        class AttemptSuccessful internal constructor(step: String) : Running(step) {
            /**
             * If [nextStep], ticks to [Attempting] state for the next step. Else, treats this
             * as end-of-pipeline, and ticks to [Terminal.Success]
             */
            override fun onSuccess(nextStep: String?): State {
                return nextStep?.let {
                    Attempting(it)
                } ?: Terminal.Success
            }

            /**
             * Should be called if the job was interrupted after the step was successfully completed.
             * Ticks to [Terminal.Failure].
             */
            override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)

            /**
            * Returns the name of the state + the name of the step it was executing.
            */
            override fun toString(): String = "${super.toString()}(step=$step)"
        }
    }

    /**
     * Represents a job that has finished running.
     */
    sealed class Terminal : State() {
        /**
         * Represents a job that finished successfully.
         */
        object Success : Terminal() {
            /**
             * Usually, this should never be called. Calling it with a null [nextStep] is a no-op.
             *
             * @throws IllegalArgumentException if nextStep != null (as a sanity check).
             */
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != null) {
                    throw IllegalArgumentException("nextStep must be null for ${this::class.java.name}.")
                }
                return this
            }

            /**
             * Should ideally never be needed. But can optionally be used to mark any unexpected errors
             * after the success state. Ticks to [Failure].
             */
            override fun onFailure(cause: Throwable): State = Failure(cause)
        }

        /**
         * Represents a job that failed.
         *
         * @param cause The [Throwable] that caused the job to fail.
         */
        class Failure internal constructor(val cause: Throwable) : Terminal() {
            /**
             * Should never be called. A failed job can never go through a success transition.
             *
             * @throws IllegalStateException Always.
             */
            override fun onSuccess(nextStep: String?): State {
                throw IllegalStateException("${this::class.java.name} cannot have a success state following it.")
            }

            /**
             * Usually, this should never be needed. But can optionally be called if more unexpected errors are
             * encountered after the job has already failed. Ticks back to the [Failure] state, however,
             * replaces the cause with the new one.
             */
            override fun onFailure(cause: Throwable): State = Failure(cause)

            /**
             * Returns detailed description of the error that caused the failure.
             */
            override fun toString(): String = "${super.toString()}(cause=\n${cause.detailedToString()})"
        }
    }

    /**
     * Returns the state's name.
     */
    override fun toString(): String = this.javaClass.name
}
