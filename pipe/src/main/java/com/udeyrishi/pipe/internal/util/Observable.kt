/**
 * Copyright (c) 2019 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

internal typealias Observer<T> = (T) -> Unit

internal interface Observable<T> {
    fun observe(observer: Observer<T>)
    fun removeObserver(observer: Observer<T>)
    fun clearObservers()

    operator fun plusAssign(observer: Observer<T>) = observe(observer)
    operator fun minusAssign(observer: Observer<T>) = removeObserver(observer)
}

internal class MutableObservable<T>(private var value: T) : Observable<T> {
    private val lock = Any()
    private val observers = mutableListOf<Observer<T>>()

    fun postValue(value: T) {
        synchronized(lock) {
            this.value = value
            observers.forEach {
                it(value)
            }
        }
    }

    override fun observe(observer: Observer<T>): Unit = synchronized(lock) {
        observers.add(observer)
    }

    override fun removeObserver(observer: Observer<T>): Unit = synchronized(lock) {
        observers.remove(observer)
    }

    override fun clearObservers(): Unit = synchronized(lock) {
        observers.clear()
    }
}
