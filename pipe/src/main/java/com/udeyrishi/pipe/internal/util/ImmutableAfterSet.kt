/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class ImmutableAfterSet<T>(private var value: T) : ReadWriteProperty<Any, T> {
    private val lock = Any()
    private var count = 0

    override fun getValue(thisRef: Any, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        synchronized(lock) {
            if (count > 0) {
                throw IllegalStateException("Cannot set property '${property.name}' in class '${thisRef::class.java.simpleName}' twice.")
            }
            ++count
            this.value = value
        }
    }
}

internal fun <T> immutableAfterSet(initialValue: T): ReadWriteProperty<Any, T> = ImmutableAfterSet(initialValue)
