/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MutableObservableTest {
    private lateinit var observable: MutableObservable<Int>
    private var observedValue: Int? = null
    private lateinit var observer: Observer<Int>

    @Before
    fun setup() {
        observable = MutableObservable(-1)
        observedValue = null
        observer = {
            observedValue = it
        }
        observable += observer
    }

    @Test
    fun `does not broadcast initial value`() {
        assertNull(observedValue)
    }

    @Test
    fun `broadcasts changes`() {
        observable.postValue(1)
        assertEquals(1, observedValue)
    }

    @Test
    fun `stops broadcasting when unsubscribed`() {
        var observedValue2: Int? = null
        observable += {
            observedValue2 = it
        }

        observable.postValue(1)
        observable -= observer
        observable.postValue(2)

        assertEquals(1, observedValue)
        assertEquals(2, observedValue2)
    }

    @Test
    fun `stops broadcasting when all subscribers are cleared`() {
        var observedValue2: Int? = null
        observable += {
            observedValue2 = it
        }

        assertNull(observedValue)
        assertNull(observedValue2)
        observable.postValue(1)
        assertEquals(1, observedValue)
        assertEquals(1, observedValue2)

        observable.clearObservers()
        observable.postValue(2)

        assertEquals(1, observedValue)
        assertEquals(1, observedValue2)
    }

    @Test
    fun `broadcasts duplicates`() {
        observable.clearObservers()
        observer = {
            observedValue = (observedValue ?: 0) + it
        }
        observable += observer

        assertNull(observedValue)
        observable.postValue(2)
        observable.postValue(2)
        assertEquals(4, observedValue)
    }
}
