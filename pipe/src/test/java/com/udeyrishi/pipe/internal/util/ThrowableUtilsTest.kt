/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ThrowableUtilsTest {
    @Test
    fun works() {
        var attempts = 0
        var fail = true
        val result = repeatUntilSucceeds<IllegalArgumentException, Int> {
            ++attempts
            if (fail) {
                fail = false
                throw IllegalArgumentException("boom")
            }
            42
        }

        assertEquals(42, result)
        assertEquals(2, attempts)
    }

    @Test(expected = IllegalStateException::class)
    fun `allows unexpected exceptions to bubble through`() {
        repeatUntilSucceeds<IllegalArgumentException, Nothing> {
            throw IllegalStateException("boom")
        }
    }
}
