/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.pipeline

import com.udeyrishi.pipe.CountedBarrierController
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.Step
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.barrier.BarrierImpl
import com.udeyrishi.pipe.internal.barrier.CountedBarrierControllerImpl
import com.udeyrishi.pipe.internal.barrier.ManualBarrierControllerImpl
import com.udeyrishi.pipe.internal.steps.StepDescriptor
import com.udeyrishi.pipe.repository.DuplicateUUIDException
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.util.Logger
import java.util.UUID
import kotlin.coroutines.CoroutineContext

internal class PipelineImpl<T : Any>(private val repository: MutableRepository<in Job<T>>, private val operations: List<PipelineOperationSpec<T>>, private val launchContext: CoroutineContext, private val logger: Logger?) : Pipeline<T> {
    private val barrierControllers by lazy {
        operations
                .asSequence()
                .filterIsInstance<PipelineOperationSpec.Barrier<T>>()
                .map {
                    when (it) {
                        is PipelineOperationSpec.Barrier.Manual<T> -> ManualBarrierControllerImpl<Passenger<T>>()
                        is PipelineOperationSpec.Barrier.Counted<T> -> CountedBarrierControllerImpl(capacity = it.capacity, onBarrierLiftedAction = it.onBarrierLiftedAction?.toPassengerStep(), launchContext = launchContext)
                    }
                }
                .toList()
    }

    override val manualBarriers by lazy {
        barrierControllers.filterIsInstance<ManualBarrierController>()
    }

    override val countedBarriers by lazy {
        barrierControllers.filterIsInstance<CountedBarrierController>()
    }

    private val countedBarrierCapacityLock = Any()

    override fun push(input: T, tag: String?): Job<T> {
        while (true) {
            val uuid = UUID.randomUUID()
            val passenger = Passenger(input, uuid, createdAt = System.currentTimeMillis())
            val steps = materializeSteps()
            val orchestrator = Orchestrator(passenger, steps, launchContext, failureListener = {
                synchronized(countedBarrierCapacityLock) {
                    barrierControllers
                            .asSequence()
                            .filterIsInstance<CountedBarrierControllerImpl<Passenger<T>>>()
                            .filter {
                                // Do not bother the counted barriers that have reached their capacity, and hence have been lifted.
                                it.arrivalCount < it.getCapacity()
                            }
                            .forEach {
                                // This will notify them to not bother waiting. The failed job is never going to arrive.
                                it.changeCapacityDueToError(it.getCapacity() - 1)
                            }
                }
            })
            val job = Job(orchestrator)
            try {
                repository.add(tag, job)
                orchestrator.logger = logger
                orchestrator.start()
                return job
            } catch (e: DuplicateUUIDException) {
                // May throw if UUID was already taken. Super rare that UUID.randomUUID() repeats UUIDs,
                // but if it still happens, try again.
            }
        }
    }

    private fun materializeSteps(): Iterator<StepDescriptor<Passenger<T>>> {
        var barrierIndex = 0

        return operations.map { spec ->
            when (spec) {
                is PipelineOperationSpec.RegularStep<T> -> StepDescriptor(spec.name, spec.attempts) {
                    it.copy(data = spec.step(it.data))
                }
                is PipelineOperationSpec.Barrier<T> -> StepDescriptor(spec.name, spec.attempts, BarrierImpl(barrierControllers[barrierIndex++]))
            }
        }.iterator()
    }
}

private fun <T : Any> Step<List<T>>.toPassengerStep(): Step<List<Passenger<T>>> {
    return { passengers ->
        passengers
                .map { it.data } // Extract data from input passengers
                .let { this(it) } // Call the original step with the list of data
                .zip(passengers) // Create tuples of (result, input passenger)
                .map { (result, inputPassenger) -> inputPassenger.copy(data = result) } // Return a new passenger with data set to the result
    }
}
