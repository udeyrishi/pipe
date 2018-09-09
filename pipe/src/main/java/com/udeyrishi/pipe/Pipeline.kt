/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.barrier.BarrierController
import com.udeyrishi.pipe.barrier.BarrierImpl
import com.udeyrishi.pipe.barrier.CountedBarrierController
import com.udeyrishi.pipe.barrier.CountedBarrierControllerImpl
import com.udeyrishi.pipe.barrier.ManualBarrierController
import com.udeyrishi.pipe.barrier.ManualBarrierControllerImpl
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.steps.Step
import com.udeyrishi.pipe.steps.StepDescriptor
import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

class Pipeline<T : Any> private constructor(private val repository: MutableRepository<in Job<T>>, private val operations: List<PipelineOperationSpec<T>>) {
    private val barrierControllers: List<BarrierController<Passenger<T>>>

    val barriers: List<ManualBarrierController>
        get() = barrierControllers.filterIsInstance<ManualBarrierController>()

    val aggregators: List<CountedBarrierController>
        get() = barrierControllers.filterIsInstance<CountedBarrierController>()

    init {
        barrierControllers = operations
                .filterIsInstance<PipelineOperationSpec.Barrier<T>>()
                .map {
                    when (it) {
                        is PipelineOperationSpec.Barrier.Manual<T> -> ManualBarrierControllerImpl<Passenger<T>>()
                        is PipelineOperationSpec.Barrier.Counted<T> -> CountedBarrierControllerImpl(capacity = it.capacity, onBarrierLiftedAction = it.onBarrierLiftedAction?.toPassengerStep())
                    }
                }
    }

    fun push(input: T, tag: String?): Job<T> {
        @Suppress("UNCHECKED_CAST")
        return repository.add(tag) { newUUID, position ->
            val passenger = Passenger(input, newUUID, position)
            val steps = materializeSteps()
            val orchestrator = Orchestrator(passenger, steps)
            Job(orchestrator)
        } as Job<T>
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
            Passenger(result, it.uuid, it.position)
        }
    }

    private fun createBarrier(spec: PipelineOperationSpec.Barrier<T>, controller: BarrierController<Passenger<T>>): StepDescriptor<Passenger<T>> {
        val barrier = BarrierImpl(controller)
        return StepDescriptor(spec.name, maxAttempts = spec.attempts) {
            barrier.invoke(it)
        }
    }

    class Builder<T : Any> {
        private val operations = mutableListOf<PipelineOperationSpec<T>>()

        fun addStep(name: String, attempts: Long = 1, step: Step<T>) = apply {
            operations.add(PipelineOperationSpec.RegularStep(name, attempts, step))
        }

        fun addBarrier(name: String) = apply {
            operations.add(PipelineOperationSpec.Barrier.Manual(name))
        }

        fun addAggregator(name: String, capacity: Int = Int.MAX_VALUE, attempts: Long = 1, aggregationAction: Step<List<T>>? = null) = apply {
            operations.add(PipelineOperationSpec.Barrier.Counted(name, capacity = capacity, attempts = attempts, onBarrierLiftedAction = aggregationAction))
        }

        fun build(repository: MutableRepository<in Job<T>>) = Pipeline(repository, operations)
    }

    internal class Passenger<T : Any>(val data: T, override val uuid: UUID, val position: Long) : Comparable<Passenger<T>>, Identifiable {
        override fun compareTo(other: Passenger<T>): Int = position.compareTo(other.position)
        override fun toString(): String = data.toString()
    }

    // Safely suppress `T`, since it's needed to prevent a class-cast in the `when` expression inside `materializeSteps`.
    @Suppress("unused")
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
            Pipeline.Passenger(result[index], originalPassenger.uuid, originalPassenger.position)
        }
    }
}

fun <T : Any> buildPipeline(repository: MutableRepository<in Job<T>>, stepDefiner: (Pipeline.Builder<T>.() -> Unit)): Pipeline<T> {
    val builder = Pipeline.Builder<T>()
    builder.stepDefiner()
    return builder.build(repository)
}

