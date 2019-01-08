/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.testutil.TestDispatcherRule
import net.jodah.concurrentunit.Waiter
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BarrierExtensionTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    @Test
    fun `lifts when condition returns true`() {
        val liftWaiter = Waiter()
        val manualBarrierController = mock<MockManualBarrierController> {
            on { lift() } doAnswer {
                liftWaiter.resume()
            }
        }

        var shouldLift = false
        val attemptWaiter = Waiter()

        manualBarrierController.liftWhen(pollingDispatcher = dispatcherRule.dispatcher) {
            attemptWaiter.resume()
            shouldLift
        }

        attemptWaiter.await(5000L, 2)
        verify(manualBarrierController, never()).lift()
        shouldLift = true
        liftWaiter.await(1000L)
    }

    @Test
    fun `stop invoking condition callback when the cancellable is cancelled`() {
        val manualBarrierController = mock<MockManualBarrierController>()
        val attemptWaiter = Waiter()
        var attempts = 0

        val cancellable = manualBarrierController.liftWhen(pollingDispatcher = dispatcherRule.dispatcher) {
            attemptWaiter.resume()
            if (++attempts > 2) {
                fail("Should not have attempted more than 2x")
            }
            false
        }

        attemptWaiter.await(5000L, 2)
        cancellable.cancel()
        assertEquals(2, attempts)
        verify(manualBarrierController, never()).lift()
    }
}

private interface MockManualBarrierController : ManualBarrierController
