/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

import com.udeyrishi.pipe.testutil.Repeat
import com.udeyrishi.pipe.testutil.RepeatRule
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class BarrierStepTest {

    @Rule
    @JvmField
    val repeatRule = RepeatRule()

    @Test
    @Repeat
    fun worksIfLiftedAfterStart() {
        val barrier = BarrierStep<String>()
        var result: String? = null
        val job = launch {
            result = barrier.blockUntilLift("input")
        }

        assertTrue(job.isActive)
        assertNull(result)

        barrier.lift()
        runBlocking {
            job.join()
        }

        assertFalse(job.isActive)
        assertEquals("input", result)
    }

    @Test
    @Repeat
    fun worksIfLiftedBeforeStart() {
        val barrier = BarrierStep<String>()
        barrier.lift()

        var result: String? = null
        val job = launch {
            result = barrier.blockUntilLift("input")
        }

        runBlocking {
            job.join()
        }

        assertFalse(job.isActive)
        assertEquals("input", result)
    }
}