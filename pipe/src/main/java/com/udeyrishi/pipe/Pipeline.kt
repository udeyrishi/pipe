/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.barrier.BarrierController
import com.udeyrishi.pipe.internal.barrier.BarrierImpl
import com.udeyrishi.pipe.internal.barrier.CountedBarrierControllerImpl
import com.udeyrishi.pipe.internal.barrier.ManualBarrierControllerImpl
import com.udeyrishi.pipe.internal.steps.StepDescriptor
import com.udeyrishi.pipe.internal.util.Identifiable
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.repository.DuplicateUUIDException
import com.udeyrishi.pipe.repository.MutableRepository
import java.util.UUID
import kotlin.coroutines.CoroutineContext

fun <T : Any> buildPipeline(repository: MutableRepository<in Job<T>>, dispatcher: PipelineDispatcher = DefaultAndroidDispatcher, stepDefiner: (Pipeline.Builder<T>.() -> Unit)): Pipeline<T> {
    val builder = Pipeline.Builder<T>()
    builder.stepDefiner()
    return builder.build(repository, dispatcher)
}

class Pipeline<T : Any> private constructor(private val repository: MutableRepository<in Job<T>>, private val operations: List<PipelineOperationSpec<T>>, private val launchContext: CoroutineContext) {
    private val barrierControllers: List<BarrierController<Passenger<T>>>

    val manualBarriers: List<ManualBarrierController>
        get() = barrierControllers.filterIsInstance<ManualBarrierController>()

    val countedBarriers: List<CountedBarrierController>
        get() = barrierControllers.filterIsInstance<CountedBarrierController>()

    private val countedBarrierCapacityLock = Any()

    init {
        barrierControllers = operations
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

    fun push(input: T, tag: String?): Job<T> {
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

        return operations.map {
            when (it) {
                is PipelineOperationSpec.RegularStep<T> -> createRegularStep(it)
                is PipelineOperationSpec.Barrier<T> -> createBarrier(it, barrierControllers[barrierIndex++])
            }
        }.iterator()
    }

    private fun createRegularStep(spec: PipelineOperationSpec.RegularStep<T>): StepDescriptor<Passenger<T>> {
        return StepDescriptor(spec.name, spec.attempts) {
            val result: T = spec.step(it.data)
            Passenger(result, it.uuid, it.createdAt)
        }
    }

    private fun createBarrier(spec: PipelineOperationSpec.Barrier<T>, controller: BarrierController<Passenger<T>>): StepDescriptor<Passenger<T>> {
        return StepDescriptor(spec.name, maxAttempts = spec.attempts, step = BarrierImpl(controller))
    }

    class Builder<T : Any> {
        private val operations = mutableListOf<PipelineOperationSpec<T>>()

        fun addStep(name: String, attempts: Long = 1, step: Step<T>) = apply {
            operations.add(PipelineOperationSpec.RegularStep(name, attempts, step))
        }

        fun addManualBarrier(name: String) = apply {
            operations.add(PipelineOperationSpec.Barrier.Manual(name))
        }

        fun addCountedBarrier(name: String, capacity: Int = Int.MAX_VALUE, attempts: Long = 1, onBarrierLiftedAction: Step<List<T>>? = null) = apply {
            operations.add(PipelineOperationSpec.Barrier.Counted(name, capacity = capacity, attempts = attempts, onBarrierLiftedAction = onBarrierLiftedAction))
        }

        fun build(repository: MutableRepository<in Job<T>>, dispatcher: PipelineDispatcher) = Pipeline(repository, operations, dispatcher.createEffectiveContext())
    }

    internal class Passenger<T : Any>(val data: T, override val uuid: UUID, val createdAt: Long) : Comparable<Passenger<T>>, Identifiable {
        override fun compareTo(other: Passenger<T>): Int = createdAt.compareTo(other.createdAt)
        override fun toString(): String = data.toString()
    }

    private sealed class PipelineOperationSpec<T : Any>(val name: String) {
        class RegularStep<T : Any>(name: String, val attempts: Long, val step: Step<T>) : PipelineOperationSpec<T>(name)

        sealed class Barrier<T : Any>(name: String, val attempts: Long) : PipelineOperationSpec<T>(name) {
            class Manual<T : Any>(name: String) : Barrier<T>(name, attempts = 1)
            class Counted<T : Any>(name: String, attempts: Long, val capacity: Int, val onBarrierLiftedAction: Step<List<T>>?) : Barrier<T>(name, attempts)
        }
    }
}

private fun <T : Any> Step<List<T>>.toPassengerStep(): Step<List<Pipeline.Passenger<T>>> {
    return { input ->
        val result: List<T> = this(input.map { it.data })
        input.mapIndexed { index, originalPassenger ->
            Pipeline.Passenger(result[index], originalPassenger.uuid, originalPassenger.createdAt)
        }
    }
}
