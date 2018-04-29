/* Copyright (c) 2018 Udey Rishi. All rights reserved. */

package com.udeyrishi.pipe

sealed class State {
    class Scheduled : State() {
        fun onSuccess(nextStep: String? = null): State {
            return nextStep?.let {
                RunningStep(it)
            } ?: TerminalSuccess()
        }

        fun onFailure(cause: Throwable) = TerminalFailure(listOf(cause))
    }

    class RunningStep private constructor(private val step: String) : State() {
        fun onSuccess(nextStep: String? = null): State {
            return nextStep?.let {
                RunningStep(it)
            } ?: TerminalSuccess()
        }

        fun onFailure(cause: Throwable) = StepFailed(cause)

        override fun toString(): String = "${super.toString()}(step=$step)"
    }

    class StepFailed(cause: Throwable) : State() {
        private val causes = mutableListOf<Throwable>().apply { add(cause) }

        fun onSuccess(retryStep: String): State = RunningStep(retryStep)

        fun onFailure(cause: Throwable? = null): State {
            if (cause != null) {
                causes.add(cause)
            }
            return TerminalFailure(causes)
        }

        override fun toString(): String = "${super.toString()}(causes=${causes.size})"
    }

    class TerminalSuccess : State()

    class TerminalFailure(val causes: List<Throwable>) : State() {
        override fun toString(): String = "${super.toString()}(causes=${causes.size})"
    }

    override fun toString(): String = this.javaClass.name
}