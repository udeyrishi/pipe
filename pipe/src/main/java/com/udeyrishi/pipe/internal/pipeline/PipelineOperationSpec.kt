/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.pipeline

import com.udeyrishi.pipe.Step

// The PipelineImpl uses the generic information
@Suppress("unused")
internal sealed class PipelineOperationSpec<T : Any>(val name: String, val attempts: Long) {
    class RegularStep<T : Any>(name: String, attempts: Long, val step: Step<T>) : PipelineOperationSpec<T>(name, attempts)

    sealed class Barrier<T : Any>(name: String, attempts: Long) : PipelineOperationSpec<T>(name, attempts) {
        class Manual<T : Any>(name: String) : Barrier<T>(name, attempts = 1)
        class Counted<T : Any>(name: String, attempts: Long, val capacity: Int, val onBarrierLiftedAction: Step<List<T>>?) : Barrier<T>(name, attempts)
    }
}
