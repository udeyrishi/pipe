/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.testutil.DefaultTestDispatcher
import kotlinx.coroutines.runBlocking
import net.jodah.concurrentunit.Waiter
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class CountedBarrierControllerTest {
    @get:Rule
    val timeoutRule = Timeout(25, TimeUnit.SECONDS)

    private lateinit var barriers: List<Barrier<String>>
    private lateinit var controller: CountedBarrierControllerImpl<String>
    private lateinit var barrierLiftWaiter: Waiter

    companion object {
        private lateinit var dispatcher: DefaultTestDispatcher

        @JvmStatic
        @BeforeClass
        fun setupClass() {
            dispatcher = DefaultTestDispatcher()
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            dispatcher.verify()
        }
    }

    @Before
    fun setup() {
        barrierLiftWaiter = Waiter()

        barriers = (1..3).map { i ->
            mock<Barrier<String>> {
                on { input } doReturn "mockInput$i"
                on { lift(any()) } doAnswer {
                    barrierLiftWaiter.resume()
                }
            }
        }

        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = dispatcher.createEffectiveContext())
    }

    @Test(expected = IllegalStateException::class)
    fun `onBarrierCreated checks for capacity overflows`() {
        controller.setCapacity(1)
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
    }

    @Test
    fun `lifts barrier when arrival count matches capacity`() {
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])

        controller.onBarrierBlocked(barriers[0])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())

        controller.onBarrierBlocked(barriers[1])

        barrierLiftWaiter.await(1000, 2)

        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
    }

    @Test
    fun `can update capacity to a bigger value when blocked`() {
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[0])
        controller.setCapacity(3)
        controller.onBarrierCreated(barriers[2])
        controller.onBarrierBlocked(barriers[1])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())
        verify(barriers[2], never()).lift(any())

        controller.onBarrierBlocked(barriers[2])

        barrierLiftWaiter.await(1000, 3)

        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
        verify(barriers[2], times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a lower value when blocked`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = dispatcher.createEffectiveContext())

        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[0])
        controller.onBarrierBlocked(barriers[1])
        controller.onBarrierCreated(barriers[2])

        controller.setCapacity(3)

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())
        verify(barriers[2], never()).lift(any())

        controller.onBarrierBlocked(barriers[2])

        barrierLiftWaiter.await(1000, 3)

        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
        verify(barriers[2], times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a bigger value before start`() {
        controller.setCapacity(3)
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[0])
        controller.onBarrierCreated(barriers[2])
        controller.onBarrierBlocked(barriers[1])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())
        verify(barriers[2], never()).lift(any())

        controller.onBarrierBlocked(barriers[2])

        barrierLiftWaiter.await(1000, 3)

        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
        verify(barriers[2], times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a lower value before start`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = dispatcher.createEffectiveContext())
        controller.setCapacity(3)

        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[0])
        controller.onBarrierBlocked(barriers[1])
        controller.onBarrierCreated(barriers[2])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())
        verify(barriers[2], never()).lift(any())

        controller.onBarrierBlocked(barriers[2])

        barrierLiftWaiter.await(1000, 3)

        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
        verify(barriers[2], times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `updating capacity to arrival count lifts the barriers`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = dispatcher.createEffectiveContext())

        controller.onBarrierCreated(barriers[0])
        controller.onBarrierBlocked(barriers[0])

        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[1])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())

        controller.setCapacity(2)

        barrierLiftWaiter.await(1000, 2)
        verify(barriers[0], times(1)).lift(eq("mockInput1"))
        verify(barriers[1], times(1)).lift(eq("mockInput2"))
    }

    @Test(expected = IllegalStateException::class)
    fun `cannot update capacity to a value less than the registration count`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = dispatcher.createEffectiveContext())
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])

        controller.setCapacity(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onBarrierBlocked verifies that the barrier was registered`() {
        controller.onBarrierBlocked(barriers[0])
    }

    @Test
    fun `invokes onBarrierLiftedAction when supplied`() {
        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = dispatcher.createEffectiveContext()) {
            // the inputs come in sorted
            assertEquals(listOf("mockInput1", "mockInput2"), it)
            listOf("mockResult1", "mockResult2")
        }

        controller.onBarrierCreated(barriers[1])
        controller.onBarrierCreated(barriers[0])

        controller.onBarrierBlocked(barriers[1])

        verify(barriers[0], never()).lift(any())
        verify(barriers[1], never()).lift(any())

        // 1 is getting pushed after 2. So arriving out of order (wrt the natural order for strings)
        controller.onBarrierBlocked(barriers[0])

        barrierLiftWaiter.await(1000, 2)

        verify(barriers[0], times(1)).lift(eq("mockResult1"))
        verify(barriers[1], times(1)).lift(eq("mockResult2"))
    }

    @Test
    fun `checks that onBarrierLiftedAction returns correct sized lists`() {
        val onBarrierLiftedActionWaiter = Waiter()
        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = dispatcher.createEffectiveContext()) {
            onBarrierLiftedActionWaiter.resume()
            // the inputs come in sorted
            listOf("mockResult1")
        }

        controller.onBarrierCreated(barriers[1])
        controller.onBarrierCreated(barriers[0])

        controller.onBarrierBlocked(barriers[1])
        controller.onBarrierBlocked(barriers[0])

        onBarrierLiftedActionWaiter.await(1000)

        barriers.subList(0, 2).forEach { barrier ->
            verify(barrier).markAsFailed(argWhere {
                it is CountedBarrierControllerImpl.BarrierLiftedActionException && it.cause is IllegalArgumentException
            })
        }
    }

    @Test
    fun `errors in onBarrierLiftedAction are conveyed to the barriers as step failures`() {
        val markAsFailedWaiter = Waiter()

        val error = RuntimeException("Something went wrong")

        barriers.forEach { barrier ->
            whenever(barrier.markAsFailed(isA<CountedBarrierControllerImpl.BarrierLiftedActionException>())).thenAnswer {
                @Suppress("CAST_NEVER_SUCCEEDS")
                markAsFailedWaiter.assertEquals(error, (it.arguments[0] as CountedBarrierControllerImpl.BarrierLiftedActionException).cause)
                markAsFailedWaiter.resume()
            }
        }

        val onBarrierLiftedActionWaiter = Waiter()
        controller = CountedBarrierControllerImpl(capacity = 3, launchContext = dispatcher.createEffectiveContext()) {
            onBarrierLiftedActionWaiter.resume()
            throw error
        }

        barriers.forEach {
            controller.onBarrierCreated(it)
            controller.onBarrierBlocked(it)
        }

        onBarrierLiftedActionWaiter.await(1000)

        markAsFailedWaiter.await(5000, barriers.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot register a barrier 2x`() {
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot mark a barrier as blocked 2x`() {
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierBlocked(barriers[0])
        controller.onBarrierBlocked(barriers[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onBarrierInterrupted checks whether the barrier was registered`() {
        controller.onBarrierInterrupted(barriers[0])
    }

    @Test
    fun `controller interrupts all other barriers if any one is interrupted`() {
        controller.setCapacity(3)

        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierCreated(barriers[2])

        runBlocking {
            controller.onBarrierBlocked(barriers[0])
            controller.onBarrierBlocked(barriers[1])
        }

        verify(barriers[0], never()).interrupt()
        verify(barriers[1], never()).interrupt()
        verify(barriers[2], never()).interrupt()

        controller.onBarrierInterrupted(barriers[1])

        verify(barriers[0]).interrupt()
        verify(barriers[1], never()).interrupt()
        verify(barriers[2]).interrupt()
    }

    @Test
    fun `controller interrupts any future registrations if any one is interrupted`() {
        controller.setCapacity(3)
        controller.onBarrierCreated(barriers[0])
        controller.onBarrierInterrupted(barriers[0])

        verify(barriers[1], never()).interrupt()
        controller.onBarrierCreated(barriers[1])
        verify(barriers[1]).interrupt()

        verify(barriers[2], never()).interrupt()
        controller.onBarrierCreated(barriers[2])
        verify(barriers[2]).interrupt()
    }

    @Test
    fun `interrupting while the controller is lifting the barrier is a no-op`() {
        val onBarrierLiftedActionWaiter = Waiter()
        val continueBarrierLiftedActionWaiter = Waiter()
        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = dispatcher.createEffectiveContext()) {
            onBarrierLiftedActionWaiter.resume()
            continueBarrierLiftedActionWaiter.await(1000)
            it
        }

        controller.onBarrierCreated(barriers[0])
        controller.onBarrierCreated(barriers[1])
        controller.onBarrierBlocked(barriers[0])
        controller.onBarrierBlocked(barriers[1])

        onBarrierLiftedActionWaiter.await(1000)

        controller.onBarrierInterrupted(barriers[0])
        controller.onBarrierInterrupted(barriers[1])

        verify(barriers[0], never()).lift(isA())
        verify(barriers[1], never()).lift(isA())

        continueBarrierLiftedActionWaiter.resume()

        verify(barriers[0], never()).interrupt()
        verify(barriers[1], never()).interrupt()

        verify(barriers[0]).lift(eq(barriers[0].input))
        verify(barriers[1]).lift(eq(barriers[1].input))
    }
}
