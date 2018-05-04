/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repeat

class RepeatRule : TestRule {
    companion object {
        private const val ENV_VAR_KEY = "REPEAT"
        private const val DEFAULT_REPEAT = 1
    }

    override fun apply(statement: Statement, description: Description): Statement {
        return description.getAnnotation(Repeat::class.java)?.let {
            RepeatStatement(statement, repeat = System.getenv(ENV_VAR_KEY)?.toInt() ?: DEFAULT_REPEAT)
        } ?: statement
    }

    private class RepeatStatement(private val statement: Statement, private val repeat: Int) : Statement() {
        override fun evaluate() {
            for (i in 0 until repeat) {
                statement.evaluate()
            }
        }
    }
}