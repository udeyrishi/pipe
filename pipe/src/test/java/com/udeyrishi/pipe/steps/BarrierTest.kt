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
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class BarrierTest {

    @Rule
    @JvmField
    val repeatRule = RepeatRule()

    @Rule
    @JvmField
    val rule = Timeout(25, TimeUnit.SECONDS)

    @Test
    @Repeat
    fun worksIfLiftedAfterStart() {
        val barrier = BarrierImpl<String>("barrier")
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
        val barrier = BarrierImpl<String>("barrier")
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

    @Test
    @Repeat
    fun worksWithMultipleInputs() {
        val barrier = BarrierImpl<String>("barrier")

        var result1: String? = null
        var result2: String? = null
        val job1 = launch {
            result1 = barrier.blockUntilLift("input1")
        }

        val job2 = launch {
            result2 = barrier.blockUntilLift("input2")
        }

        barrier.lift()

        runBlocking {
            job1.join()
            job2.join()
        }

        assertFalse(job1.isActive)
        assertFalse(job2.isActive)
        assertEquals("input1", result1)
        assertEquals("input2", result2)
    }

    @Test
    fun blockCountIsCorrect() {
        val barrier = BarrierImpl<String>("barrier")
        assertEquals(0, barrier.blockedCount)

        val job1 = launch {
            barrier.blockUntilLift("input1")
        }

        val job2 = launch {
            barrier.blockUntilLift("input2")
        }

        while (barrier.blockedCount < 2) {
            Thread.sleep(100)
        }

        barrier.lift()

        runBlocking {
            job1.join()
            job2.join()
        }

        assertEquals(0, barrier.blockedCount)
    }
}
