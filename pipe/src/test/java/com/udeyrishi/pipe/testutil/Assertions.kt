/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import org.junit.Assert.fail

internal inline fun <reified T> Any?.assertIs() {
    if (this !is T) {
        val expectedType = T::class.java.name
        val actualType = if (this == null) "<null>" else this::class.java.name
        val objectDescription = if (this == null) "" else ": $this"

        fail("Expected object to be of type $expectedType, but was $actualType$objectDescription")
    }
}
