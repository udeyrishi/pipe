/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

sealed class State {
    internal abstract fun onSuccess(nextStep: String? = null): State
    internal abstract fun onFailure(cause: Throwable): State

    object Scheduled : State() {
        override fun onSuccess(nextStep: String?): State {
            return nextStep?.let {
                Running.Attempting(it)
            } ?: Terminal.Success
        }

        override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)
    }

    sealed class Running(val step: String) : State() {
        class Attempting internal constructor(step: String) : Running(step) {
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != null) {
                    throw IllegalArgumentException("nextStep must be null for ${this::class.java.name}.")
                }

                return AttemptSuccessful(step)
            }

            override fun onFailure(cause: Throwable): State = AttemptFailed(step, cause)

            override fun toString(): String = "${super.toString()}(step=$step)"
        }

        class AttemptFailed internal constructor(step: String, val cause: Throwable) : Running(step) {
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != step) {
                    throw IllegalArgumentException("nextStep must be the same as step for ${this::class.java.name}.")
                }

                return Attempting(step)
            }

            override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)

            override fun toString(): String = "${super.toString()}(cause=\n${cause.detailedToString()})"
        }

        class AttemptSuccessful internal constructor(step: String) : Running(step) {
            override fun onSuccess(nextStep: String?): State {
                return nextStep?.let {
                    Attempting(it)
                } ?: Terminal.Success
            }

            override fun onFailure(cause: Throwable): State = Terminal.Failure(cause)

            override fun toString(): String = "${super.toString()}(step=$step)"
        }
    }

    sealed class Terminal : State() {
        object Success : Terminal() {
            override fun onSuccess(nextStep: String?): State {
                if (nextStep != null) {
                    throw IllegalArgumentException("nextStep must be null for ${this::class.java.name}.")
                }
                return this
            }

            override fun onFailure(cause: Throwable): State = Failure(cause)
        }

        class Failure internal constructor(@Suppress("MemberVisibilityCanBePrivate") val cause: Throwable) : Terminal() {
            override fun onSuccess(nextStep: String?): State {
                throw IllegalStateException("${this::class.java.name} cannot have a success state following it.")
            }

            override fun onFailure(cause: Throwable): State = Failure(cause)

            override fun toString(): String = "${super.toString()}(cause=\n${cause.detailedToString()})"
        }
    }

    override fun toString(): String = this.javaClass.name
}

private fun Throwable.detailedToString(): String {
    var level = 0
    val sb = StringBuilder()
    var cause: Throwable? = this

    while (cause != null) {
        if (level++ > 0) {
            sb.append("\nCaused by: ")
        }
        sb.append(cause)
        cause = cause.cause
    }

    return sb.toString()
}
