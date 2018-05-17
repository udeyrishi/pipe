/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

import java.util.UUID

class Pipeline<T : Any> private constructor(private val steps: List<StepDescriptor<Passenger<T>>>) {
    private val uuidsToTrackers = mutableMapOf<UUID, Orchestrator<Passenger<T>>>()

    val orchestrators: List<Orchestrator<Passenger<T>>>
        get() = listOf(*uuidsToTrackers.values.toTypedArray())

    fun push(input: T): Orchestrator<Passenger<T>> {
        return synchronized(this) {
            val position = orchestrators.size.toLong()
            val uuid = generateUuid()
            val passenger = Passenger(input, uuid, position)
            val tracker = Orchestrator(passenger, steps.iterator())
            uuidsToTrackers.put(uuid, tracker)
            tracker
        }
    }

    operator fun get(uuid: UUID): Orchestrator<Passenger<T>>? {
        return uuidsToTrackers[uuid]
    }

    private fun generateUuid(): UUID {
        var uuid = UUID.randomUUID()
        while (uuid in uuidsToTrackers) {
            uuid = UUID.randomUUID()
        }
        return uuid
    }

    @Suppress("EXPERIMENTAL_FEATURE_WARNING")
    class Builder<T : Any>(private val ordered: Boolean = true) {
        private val steps = mutableListOf<StepDescriptor<Passenger<T>>>()
        private var barrierCount = 0
        private var aggregatorCount = 0

        fun addStep(name: String, attempts: Long = 1, step: suspend (T) -> T) {
            synchronized(this) {
                steps.add(StepDescriptor(name, attempts) {
                    val result: T = step(it.data)
                    Passenger(result, it.uuid, it.position)
                })
            }
        }

        fun addBarrier(): Barrier<T> {
            val barrier = Barrier<T>()
            synchronized(this) {
                steps.add(StepDescriptor(name = "Barrier${barrierCount++}", maxAttempts = 1) {
                    val result: T = barrier.blockUntilLift(it.data)
                    Passenger(result, it.uuid, it.position)
                })
            }
            return barrier
        }

        fun addAggregator(capacity: Int, attempts: Long = 1, aggregationAction: suspend (List<T>) -> List<T>) {
            val aggregator = Aggregator<Passenger<T>>(capacity, ordered) {
                val result: List<T> = aggregationAction(it.map { it.data })
                it.mapIndexed { index, originalPassenger ->
                    Passenger(result[index], originalPassenger.uuid, originalPassenger.position)
                }
            }
            synchronized(this) {
                steps.add(StepDescriptor(name = "Aggregator${aggregatorCount++}", maxAttempts = attempts) {
                    aggregator.push(it)
                })
            }
        }

        fun build(): Pipeline<T> {
            return synchronized(this) {
                Pipeline(steps.map { it })
            }
        }
    }

    /**
     * Represents an object that travels in a pipeline. A Orchestrator helps it navigate the pipeline.
     */
    class Passenger<T : Any>(val data: T, val uuid: UUID, val position: Long) : Comparable<Passenger<T>> {
        override fun compareTo(other: Passenger<T>): Int = position.compareTo(other.position)

        override fun toString(): String {
            return data.toString()
        }
    }
}

fun <T : Any> buildPipeline(ordered: Boolean = true, stepDefiner: (Pipeline.Builder<T>.() -> Unit)): Pipeline<T> {
    val builder = Pipeline.Builder<T>(ordered)
    builder.stepDefiner()
    return builder.build()
}