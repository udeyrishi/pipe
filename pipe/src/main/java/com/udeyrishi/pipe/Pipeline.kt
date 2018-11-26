/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.internal.pipeline.PipelineImpl
import com.udeyrishi.pipe.internal.pipeline.PipelineOperationSpec
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.util.Logger

/**
 * A pipeline is a sequence of steps (and barriers, optionally) used to convert an input to an output.
 *
 *
 * Important note regarding the scope of barriers within pipelines:
 *
 * Note that all the barriers will be applicable to all the [Job] that will be [push]-started
 * via this _instance_ of the [Pipeline]. So if you plan on reusing the same schematic for the pipeline
 * for multiple groups of inputs, but the barriers need to be applied only to 1 group at a time,
 * make sure that you're creating new instances of the same Pipeline. This can be done by using a reusable
 * factory functions that create pipeline instances following the same schematic.
 *
 * e.g.
 *
 * ```
 * // Some factory function.
 * fun makeMyPipeline(): Pipeline<Int> {
 *     return buildPipeline {
 *           // define steps
 *     }
 * }
 *
 * val group1 = listOf(1,2,3)
 * val group2 = listOf(5,6,7)
 *
 * val pipeline1 = makeMyPipeline()
 * val jobGroup1 = group1.map { pipeline1.push(it) }
 *
 * val pipeline2 = makeMyPipeline()
 * val jobGroup2 = group2.map { pipeline2.push(it) }
 * ```
 *
 * These two pipelines have the same schematic; meaning they will execute the same steps. However,
 * `jobGroup1` and `jobGroup2` do not share their barriers. (1,2,3) will be treated as job siblings,
 * while (5,6,7) will be treated as another group of siblings.
 */
interface Pipeline<T : Any> {
    /**
     * Returns the list of all the manual barriers added to the pipeline. They are ordered in the same way
     * as they were added.
     */
    val manualBarriers: List<ManualBarrierController>

    /**
     * Returns the list of all the counted barriers added to the pipeline. They are ordered in the same way
     * as they were added.
     */
    val countedBarriers: List<CountedBarrierController>

    /**
     * Pushes an input into the pipeline. Returns the [Job] that can be used for monitoring the flow
     * of this input through the pipeline.
     *
     * - An optional tag can be provided to logically group jobs. Jobs can be queried by tags on the
     * [com.udeyrishi.pipe.repository.Repository] that was supplied to the pipeline.
     */
    fun push(input: T, tag: String?): Job<T>
}

/**
 * A DSL for building a pipeline. See the backing [PipelineBuilder] for seeing all possible setup operations
 * available.
 */
fun <T : Any> buildPipeline(stepDefiner: (PipelineBuilder<T>.() -> Unit)): Pipeline<T> {
    val builder = PipelineBuilder<T>()
    builder.stepDefiner()
    return builder.build()
}

/**
 * A builder class for constructing pipelines.
 */
class PipelineBuilder<T : Any> {
    private val operations = mutableListOf<PipelineOperationSpec<T>>()
    private var dispatcher: PipelineDispatcher = DefaultAndroidDispatcher
    private var repository: MutableRepository<in Job<T>> = InMemoryRepository()
    private var logger: Logger? = null

    /**
     * Adds a step in the pipeline.
     *
     * @param name A name for this step. This should ideally be unique, but it's not required. This
     *             will appear in the logs and any exception messages.
     * @param attempts The maximum number of attempts to be made in case this step throws an exception.
     * @param step A **pure** input-output function defining the business logic in the step.
     *             Note that we cannot prevent you from affecting any global state in the body of the step.
     *             However, that is almost always a bad idea. This function will be shared among the
     *             different jobs that will be push-started via this pipeline. Additionally, it may
     *             be invoked multiple times due to retries. Therefore, it's best to architect your pipeline
     *             such that they only use pure functional steps.
     */
    fun addStep(name: String, attempts: Long = 1, step: Step<T>) = apply {
        operations.add(PipelineOperationSpec.RegularStep(name, attempts, step))
    }

    /**
     * Adds a manual barrier in the pipeline. A handle to the corresponding [ManualBarrierController]
     * can be retrieved once the pipeline is created via [Pipeline.manualBarriers]::get(i).
     *
     * @param name A name for this barrier. This should ideally be unique, but it's not required. This
     *             will appear in the logs and any exception messages.
     */
    fun addManualBarrier(name: String) = apply {
        operations.add(PipelineOperationSpec.Barrier.Manual(name))
    }

    /**
     * Adds a counted barrier in the pipeline. A handle to the corresponding [CountedBarrierController]
     * can be retrieved once the pipeline is created via [Pipeline.countedBarriers]::get(i).
     *
     * @param name A name for this barrier. This should ideally be unique, but it's not required. This
     *             will appear in the logs and any exception messages.
     * @param capacity The initial capacity for this counted barrier.
     * @param attempts The maximum number of attempts to be made in case [onBarrierLiftedAction] throws an exception.
     * @param onBarrierLiftedAction An optional aggregation task that can be performed on the list of items
     *                              received from the previous step.
     *
     *                              It'll receive the results of the previous step, joined together in a list,
     *                              as its input. This list will be sorted based on the timestamp the
     *                              corresponding job was started.
     *
     *                              Ideally, the size of the input list will be equal to [capacity]. However,
     *                              if one of the jobs had failed before reaching the barrier, the size may be lower.
     *
     *                              The size of the returned list must be the same as the input. Else, it'll be
     *                              considered a failed [onBarrierLiftedAction], consuming one of the allowed
     *                              attempts.
     */
    fun addCountedBarrier(name: String, capacity: Int = Int.MAX_VALUE, attempts: Long = 1, onBarrierLiftedAction: Step<List<T>>? = null) = apply {
        operations.add(PipelineOperationSpec.Barrier.Counted(name, capacity = capacity, attempts = attempts, onBarrierLiftedAction = onBarrierLiftedAction))
    }

    /**
     * Sets the dispatcher that this pipeline will use. Uses [DefaultAndroidDispatcher] by default.
     */
    fun setDispatcher(dispatcher: PipelineDispatcher) = apply {
        this.dispatcher = dispatcher
    }

    /**
     * Sets the repository that the pipeline will use for tracking the ongoing uploads. Uses an
     * [InMemoryRepository] by default.
     */
    fun setRepository(repository: MutableRepository<in Job<T>>) = apply {
        this.repository = repository
    }

    /**
     * Sets an optional logger that will log all the important state transitions happening in all the
     * jobs. `AndroidLogger` should be appropriate for most cases.
     */
    fun setLogger(logger: Logger?) = apply {
        this.logger = logger
    }

    /**
     * Builds the pipeline.
     */
    fun build(): Pipeline<T> = PipelineImpl(repository, operations, dispatcher.createEffectiveContext(), logger)
}
