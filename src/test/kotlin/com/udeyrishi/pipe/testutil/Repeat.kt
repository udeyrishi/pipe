/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repeat(val value: Int = 1)

class RepeatRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return description.getAnnotation(Repeat::class.java)?.let {
            RepeatStatement(statement, repeat = it.value)
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