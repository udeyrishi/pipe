/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class SynchronizationExtensionsTest {
    private lateinit var list: MutableList<Int>

    @Before
    fun setup() {
        list = mutableListOf()
    }

    @Test
    fun synchronizedRunHoldsLockWhenOthersRequest() {
        // job2 will stop waiting first, and get the lock.
        // By that time job1 is done waiting, and it tries to acquire the lock, it wouldn't get access, because job2 would still be holding it.
        // The resulting list would be [1, 0].
        // (job1's delayInside is pointless)
        val job1 = appendToList(0, delayOutside = 200, delayInside = 0)
        val job2 = appendToList(1, delayOutside = 100, delayInside = 300)

        runBlocking {
            job1.join()
            job2.join()
        }

        assertEquals(2, list.size)
        assertEquals(1, list[0])
        assertEquals(0, list[1])
    }

    @Test
    fun synchronizedRunHoldsLockCorrectlyUnderSimpleCase() {
        // job1 will be in and out by the time job2 tries to acquire the lock (200 + 300 < 600).
        // The execution here would be simple.
        val job1 = appendToList(0, delayOutside = 50, delayInside = 60)
        val job2 = appendToList(1, delayOutside = 150, delayInside = 0)

        runBlocking {
            job1.join()
            job2.join()
        }

        assertEquals(2, list.size)
        assertEquals(0, list[0])
        assertEquals(1, list[1])
    }

    private fun appendToList(value: Int, delayOutside: Long, delayInside: Long): Job {
        return launch {
            Thread.sleep(delayOutside)
            list.synchronizedRun {
                Thread.sleep(delayInside)
                list.add(value)
            }
        }
    }
}