/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import com.udeyrishi.pipe.PipelineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.fail
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TestDispatcherRule(private val errorExpected: Boolean = false) : TestRule {
    lateinit var dispatcher: PipelineDispatcher
        private set

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val dispatcher = DefaultTestDispatcher().also {
                    this@TestDispatcherRule.dispatcher = it
                }

                try {
                    base.evaluate()
                } finally {
                    dispatcher.verify(errorExpected)
                }
            }
        }
    }
}

private class DefaultTestDispatcher : PipelineDispatcher {
    private var lastUnhandledError: Throwable? = null

    override val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onInternalPipeError(throwable: Throwable) {
        lastUnhandledError = throwable
    }

    fun verify(errorExpected: Boolean) {
        if (errorExpected) {
            var attempt = 0
            while (lastUnhandledError == null && attempt++ < MAX_VERIFY_ATTEMPTS) {
                Thread.sleep(POLL_DELAY_MS)
            }

            if (lastUnhandledError == null && attempt == MAX_VERIFY_ATTEMPTS) {
                fail("Expected an error to be thrown, but none was received for ${POLL_DELAY_MS * MAX_VERIFY_ATTEMPTS} milliseconds.")
            }
        }

        val errorToBeThrown = lastUnhandledError
        lastUnhandledError = null
        errorToBeThrown?.let {
            throw it
        }
    }

    companion object {
        private const val MAX_VERIFY_ATTEMPTS = 1000
        private const val POLL_DELAY_MS = 10L
    }
}
