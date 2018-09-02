package com.udeyrishi.pipe.steps

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StepDescriptorTest {
    @Test(expected = IllegalArgumentException::class)
    fun maxAttemptsCannotBeZero() {
        StepDescriptor<Int>(name = "foo", maxAttempts = 0L) { it }
    }

    @Test(expected = IllegalArgumentException::class)
    fun maxAttemptsCannotBeNegative() {
        StepDescriptor<Int>(name = "foo", maxAttempts = -1L) { it }
    }
}
