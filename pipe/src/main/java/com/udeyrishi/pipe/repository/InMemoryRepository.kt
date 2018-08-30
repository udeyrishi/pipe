/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

private data class InMemoryRepositoryEntry<T : Identifiable>(override val identifiableObject: T, override val tag: String?) : RepositoryEntry<T>

class InMemoryRepository<T : Identifiable> : MutableRepository<T> {
    private val entries = arrayListOf<InMemoryRepositoryEntry<T>>()
    private val uuidIndex = hashMapOf<UUID, Int>()
    private val lock = Any()

    override fun add(tag: String?, identifiableObjectBuilder: (newUUID: UUID, position: Long) -> T): T {
        return synchronized(lock) {
            val identifiableObject = identifiableObjectBuilder(generateUuid(), entries.size.toLong())
            val entry = InMemoryRepositoryEntry(identifiableObject, tag)
            entries.add(entry)
            uuidIndex[entry.uuid] = entries.lastIndex
            identifiableObject
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

    override fun removeIf(predicate: (RepositoryEntry<T>) -> Boolean) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (predicate(item)) {
                iterator.remove()
                uuidIndex.remove(item.uuid)
            }
        }
    }

    override fun close() {}
}
