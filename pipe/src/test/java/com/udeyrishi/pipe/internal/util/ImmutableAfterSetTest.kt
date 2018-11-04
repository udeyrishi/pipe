/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ImmutableAfterSetTest {
    @Test
    fun getValueReturnsNullWhenStartedWithNull() {
        assertNull(ARandomClass().aRandomField)
    }

    @Test
    fun getValueReturnsInitialSetValue() {
        assertEquals("foo", ARandomClass("foo").aRandomField)
    }

    @Test
    fun setValueWorks() {
        val test = ARandomClass("first")
        test.aRandomField = "second"
        assertEquals("second", test.aRandomField)
    }

    @Test(expected = IllegalStateException::class)
    fun setValueChecksForSetCount() {
        val test = ARandomClass()
        test.aRandomField = "second"
        test.aRandomField = "third"
    }

    private inner class ARandomClass(initialValue: String? = null) {
        var aRandomField: String? by immutableAfterSet(initialValue)
    }
}
