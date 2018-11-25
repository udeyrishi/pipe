/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer

internal fun createMockLifecycleOwner(): LifecycleOwner {
    return object : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override fun getLifecycle() = registry
    }
}

internal class TapeLiveDataObserver<T> : Observer<T> {
    private val _items = mutableListOf<T>()
    val items: List<T>
        get() = _items

    override fun onChanged(t: T) {
        _items.add(t)
    }
}
