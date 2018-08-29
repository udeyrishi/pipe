/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import com.udeyrishi.pipe.Orchestrator
import com.udeyrishi.pipe.state.State
import java.util.UUID

private data class InMemoryRepositoryEntry<T : Identifiable>(override val orchestrator: Orchestrator<T>, override val tag: String?) : RepositoryEntry<T>

class InMemoryRepository<T : Identifiable> : MutableRepository<T> {
    private val entries = arrayListOf<InMemoryRepositoryEntry<T>>()
    private val uuidIndex = hashMapOf<UUID, Int>()
    private val lock = Any()

    override fun add(tag: String?, orchestratorBuilder: (newUUID: UUID, position: Long) -> Orchestrator<T>): Orchestrator<T> {
        return synchronized(lock) {
            val orchestrator = orchestratorBuilder(generateUuid(), entries.size.toLong())
            val entry = InMemoryRepositoryEntry(orchestrator, tag)
            entries.add(entry)
            uuidIndex[entry.uuid] = entries.lastIndex
            orchestrator
        }
    }

    private fun generateUuid(): UUID {
        var uuid = UUID.randomUUID()
        while (uuidIndex.containsKey(uuid)) {
            uuid = UUID.randomUUID()
        }
        return uuid
    }

    override fun get(uuid: UUID): RepositoryEntry<T>? {
        return synchronized(lock) {
            uuidIndex[uuid]?.let {
                entries[it]
            }
        }
    }

    override fun get(tag: String?): List<RepositoryEntry<T>> {
        return synchronized(lock) {
            entries.filter {
                it.tag == tag
            }
        }
    }

    override fun prune(removeFailures: Boolean) {
        synchronized(lock) {
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()

                val shouldRemove = when (item.orchestrator.state) {
                    is State.Terminal.Success -> true
                    is State.Terminal.Failure -> removeFailures
                    is State.Terminal -> throw NotImplementedError("Support for new terminal state ${item.orchestrator.state} not added.")
                    else -> false
                }

                if (shouldRemove) {
                    iterator.remove()
                    uuidIndex.remove(item.uuid)
                }
            }
        }
    }

    override fun close() { }
}