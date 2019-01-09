/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import com.udeyrishi.pipe.PipelineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TestDispatcherRule : TestRule {
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
                    dispatcher.verify()
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

    fun verify() {
        lastUnhandledError?.let {
            throw it
        }
    }
}
