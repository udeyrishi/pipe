/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

sealed class State {
    class Scheduled internal constructor() : State() {
        fun onSuccess(nextStep: String? = null): State {
            return nextStep?.let {
                Running.Attempting(it)
            } ?: Terminal.Success()
        }

        fun onFailure(cause: Throwable) = Terminal.Failure(listOf(cause))
    }

    sealed class Running : State() {
        class Attempting internal constructor(val step: String) : Running() {
            fun onSuccess(nextStep: String? = null): State {
                return nextStep?.let {
                    Attempting(it)
                } ?: Terminal.Success()
            }

            fun onFailure(cause: Throwable) = AttemptFailed(cause)

            override fun toString(): String = "${super.toString()}(step=$step)"
        }

        class AttemptFailed internal constructor(cause: Throwable) : Running() {
            private val _causes = mutableListOf<Throwable>().apply { add(cause) }

            val causes: List<Throwable>
                get() = _causes.toList()

            fun onSuccess(retryStep: String) = Attempting(retryStep)

            fun onFailure(cause: Throwable? = null): Terminal.Failure {
                if (cause != null) {
                    _causes.add(cause)
                }
                return Terminal.Failure(_causes)
            }

            override fun toString(): String = "${super.toString()}(causes=${_causes.size})"
        }
    }

    sealed class Terminal : State() {
        class Success internal constructor() : Terminal()

        class Failure internal constructor(val causes: List<Throwable>) : Terminal() {
            override fun toString(): String = "${super.toString()}(causes=${causes.size})"
        }
    }

    override fun toString(): String = this.javaClass.simpleName
}