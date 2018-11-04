/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.internal.pipeline.PipelineImpl
import com.udeyrishi.pipe.internal.pipeline.PipelineOperationSpec
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository

interface Pipeline<T : Any> {
    val manualBarriers: List<ManualBarrierController>
    val countedBarriers: List<CountedBarrierController>

    fun push(input: T, tag: String?): Job<T>
}

fun <T : Any> buildPipeline(stepDefiner: (PipelineBuilder<T>.() -> Unit)): Pipeline<T> {
    val builder = PipelineBuilder<T>()
    builder.stepDefiner()
    return builder.build()
}

class PipelineBuilder<T : Any> {
    private val operations = mutableListOf<PipelineOperationSpec<T>>()
    private var dispatcher: PipelineDispatcher = DefaultAndroidDispatcher
    private var repository: MutableRepository<in Job<T>> = InMemoryRepository()

    fun addStep(name: String, attempts: Long = 1, step: Step<T>) = apply {
        operations.add(PipelineOperationSpec.RegularStep(name, attempts, step))
    }

    fun addManualBarrier(name: String) = apply {
        operations.add(PipelineOperationSpec.Barrier.Manual(name))
    }

    fun addCountedBarrier(name: String, capacity: Int = Int.MAX_VALUE, attempts: Long = 1, onBarrierLiftedAction: Step<List<T>>? = null) = apply {
        operations.add(PipelineOperationSpec.Barrier.Counted(name, capacity = capacity, attempts = attempts, onBarrierLiftedAction = onBarrierLiftedAction))
    }

    fun setDispatcher(dispatcher: PipelineDispatcher) = apply {
        this.dispatcher = dispatcher
    }

    fun setRepository(repository: MutableRepository<in Job<T>>) = apply {
        this.repository = repository
    }

    fun build(): Pipeline<T> = PipelineImpl(repository, operations, dispatcher.createEffectiveContext())
}
