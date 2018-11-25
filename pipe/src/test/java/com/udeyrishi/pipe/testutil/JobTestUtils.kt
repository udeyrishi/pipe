/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.udeyrishi.pipe.State
import net.jodah.concurrentunit.Waiter

internal fun LiveData<State>.waitTill(condition: (State) -> Boolean) = listOf(this).waitTill(condition)

internal fun List<LiveData<State>>.waitTill(condition: (State) -> Boolean) {
    val jobCompletedWaiter = Waiter()
    forEach { liveData ->
        liveData.observe(createMockLifecycleOwner(), Observer {
            if (condition(it)) {
                jobCompletedWaiter.resume()
            }
        })
    }

    jobCompletedWaiter.await(5000, size)
}
