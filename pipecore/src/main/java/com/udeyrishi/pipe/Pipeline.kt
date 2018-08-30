/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.steps.Aggregator
import com.udeyrishi.pipe.steps.AggregatorImpl
import com.udeyrishi.pipe.steps.Barrier
import com.udeyrishi.pipe.steps.BarrierImpl
import com.udeyrishi.pipe.steps.Step
import com.udeyrishi.pipe.steps.StepDescriptor
import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

class Pipeline<T : Any> private constructor(private val steps: List<StepDescriptor<Passenger<T>>>, private val repository: MutableRepository<Job<T>>) {
    fun push(input: T, tag: String?): Job<T> {
        return repository.add(tag) { newUUID, position ->
            val passenger = Passenger(input, newUUID, position)
            val orchestrator = Orchestrator(passenger, steps.iterator())
            Job(orchestrator)
        }
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    class Builder<T : Any>(private val ordered: Boolean = true) {
        private val steps = mutableListOf<StepDescriptor<Passenger<T>>>()
        private var barrierCount = 0
        private var aggregatorCount = 0

        fun addStep(name: String, attempts: Long = 1, step: Step<T>) {
            steps.add(StepDescriptor(name, attempts) {
                val result: T = step(it.data)
                Passenger(result, it.uuid, it.position)
            })
        }

        fun addBarrier(): Barrier {
            val barrier = BarrierImpl<T>()
            steps.add(StepDescriptor(name = "Barrier${barrierCount++}", maxAttempts = 1) {
                val result: T = barrier.blockUntilLift(it.data)
                Passenger(result, it.uuid, it.position)
            })
            return barrier
        }

        fun addAggregator(capacity: Int, attempts: Long = 1, aggregationAction: Step<List<T>>): Aggregator {
            val aggregator = AggregatorImpl<Passenger<T>>(capacity, ordered) { input ->
                val result: List<T> = aggregationAction(input.map { it.data })
                input.mapIndexed { index, originalPassenger ->
                    Passenger(result[index], originalPassenger.uuid, originalPassenger.position)
                }
            }
            steps.add(StepDescriptor(name = "Aggregator${aggregatorCount++}", maxAttempts = attempts) {
                aggregator.push(it)
            })
            return aggregator
        }

        fun build(repository: MutableRepository<Job<T>>): Pipeline<T> {
            return Pipeline(steps.map { it }, repository)
        }
    }

    /**
     * Represents an object that travels in a pipeline. A Orchestrator helps it navigate the pipeline.
     */
    internal class Passenger<T : Any>(val data: T, override val uuid: UUID, val position: Long) : Comparable<Passenger<T>>, Identifiable {
        override fun compareTo(other: Passenger<T>): Int = position.compareTo(other.position)

        override fun toString(): String {
            return data.toString()
        }
    }
}

fun <T : Any> buildPipeline(repository: MutableRepository<Job<T>>, ordered: Boolean = true, stepDefiner: (Pipeline.Builder<T>.() -> Unit)): Pipeline<T> {
    val builder = Pipeline.Builder<T>(ordered)
    builder.stepDefiner()
    return builder.build(repository)
}