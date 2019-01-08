/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.testutil.TestDispatcherRule
import kotlinx.coroutines.runBlocking
import net.jodah.concurrentunit.Waiter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class ManualBarrierControllerTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule()

    private lateinit var liftWaiter1: Waiter
    private lateinit var liftWaiter2: Waiter
    private lateinit var mockBarrier1: Barrier<String>
    private lateinit var mockBarrier2: Barrier<String>
    private lateinit var controller: ManualBarrierControllerImpl<String>

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    @Before
    fun setup() {
        liftWaiter1 = Waiter()
        liftWaiter2 = Waiter()
        mockBarrier1 = mock {
            on { lift(isNull()) } doAnswer {
                liftWaiter1.resume()
            }
        }
        mockBarrier2 = mock {
            on { lift(isNull()) } doAnswer {
                liftWaiter2.resume()
            }
        }
        controller = ManualBarrierControllerImpl(dispatcherRule.dispatcher.createEffectiveContext())
    }

    @Test
    fun `lifts barriers immediately upon creation once the controller is lifted`() {
        controller.lift()
        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        liftWaiter1.await(500)
        liftWaiter2.await(500)

        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2).lift(isNull())
    }

    @Test
    fun `lifts registered barriers when the controller is lifted`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.lift()

        liftWaiter1.await(500)
        liftWaiter2.await(500)

        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2).lift(isNull())
    }

    @Test
    fun `lifts blocked barriers when the controller is lifted`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier2)

        controller.lift()

        liftWaiter1.await(500)
        liftWaiter2.await(500)

        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2).lift(isNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `only allows registered barriers to notify of blocks`() = runBlocking {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier2)
    }

    @Test
    fun `lifting 2x is a no-op`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier2)

        controller.lift()
        controller.lift()

        liftWaiter1.await(500)
        liftWaiter2.await(500)

        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2).lift(isNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot register a barrier 2x`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot mark a barrier as blocked 2x`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onBarrierInterrupted checks whether the barrier was registered`() {
        controller.onBarrierInterrupted(mockBarrier1)
    }

    @Test
    fun `interrupted barriers are not lifted by the controller`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierInterrupted(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())
        controller.lift()

        liftWaiter1.await(500)

        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2, never()).lift(any())
    }
}
