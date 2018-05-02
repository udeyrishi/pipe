/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class ImmutableAfterSet<T>(private var value: T) : ReadWriteProperty<Any, T> {
    private var count = 0

    override fun getValue(thisRef: Any, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        synchronized(this) {
            if (count > 0) {
                throw IllegalStateException("Cannot set property '${property.name}' in class '${thisRef::class.java.simpleName}' twice.")
            }
            ++count
            this.value = value
        }
    }
}

fun <T> immutableAfterSet(initialValue: T): ReadWriteProperty<Any, T> = ImmutableAfterSet(initialValue)