package com.udeyrishi.pipe.androidktx

import android.arch.lifecycle.DefaultLifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import com.udeyrishi.pipe.Identifiable
import com.udeyrishi.pipe.Orchestrator
import com.udeyrishi.pipe.StateChangeListener

fun <T : Identifiable> Orchestrator<T>.subscribe(owner: LifecycleOwner, stateChangeListener: StateChangeListener) {
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            subscribe(stateChangeListener)
        }

        override fun onPause(owner: LifecycleOwner) {
            unsubscribe(stateChangeListener)
        }
    })
}