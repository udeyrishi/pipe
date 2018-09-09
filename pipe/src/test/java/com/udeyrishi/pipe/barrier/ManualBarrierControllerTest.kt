/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.barrier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class ManualBarrierControllerTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule()

    @Mock
    internal lateinit var mockBarrier1: Barrier<String>

    @Mock
    internal lateinit var mockBarrier2: Barrier<String>

    private lateinit var controller: ManualBarrierControllerImpl<String>

    @Before
    fun setup() {
        controller = ManualBarrierControllerImpl()
    }

    @Test
    fun `lifts barriers immediately upon creation once the controller is lifted`() {
        controller.lift()
        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.onBarrierCreated(mockBarrier1)
        verify(mockBarrier1).lift(isNull())

        controller.onBarrierCreated(mockBarrier2)
        verify(mockBarrier2).lift(isNull())
    }


    @Test
    fun `lifts registered barriers when the controller is lifted`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.lift()
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
        verify(mockBarrier1).lift(isNull())
        verify(mockBarrier2).lift(isNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `only allows registered barriers to notify of blocks`() {
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
}
