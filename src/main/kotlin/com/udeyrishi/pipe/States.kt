/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
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

    class RunningStep(val step: String) : State() {
        fun onSuccess(nextStep: String? = null): State {
            return nextStep?.let {
                RunningStep(it)
            } ?: TerminalSuccess()
        }

        fun onFailure(cause: Throwable) = StepFailed(cause)

        override fun toString(): String = "${super.toString()}(step=$step)"
    }

    class StepFailed(cause: Throwable) : State() {
        private val _causes = mutableListOf<Throwable>().apply { add(cause) }

        val causes: List<Throwable>
            get() = _causes.toList()

        fun onSuccess(retryStep: String) = RunningStep(retryStep)

        fun onFailure(cause: Throwable? = null): TerminalFailure {
            if (cause != null) {
                _causes.add(cause)
            }
            return TerminalFailure(_causes)
        }

        override fun toString(): String = "${super.toString()}(causes=${_causes.size})"
    }

    class TerminalSuccess : State()

    class TerminalFailure(val causes: List<Throwable>) : State() {
        override fun toString(): String = "${super.toString()}(causes=${causes.size})"
    }

    override fun toString(): String = this.javaClass.simpleName
}