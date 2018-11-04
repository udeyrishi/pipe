/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.internal.util.Identifiable
import java.util.UUID

private data class InMemoryRepositoryEntry<T : Identifiable>(override val identifiableObject: T, override val tag: String?) : RepositoryEntry<T>

class InMemoryRepository<T : Identifiable> : MutableRepository<T> {
    private val entries = arrayListOf<InMemoryRepositoryEntry<T>>()
    private val uuidIndex = hashMapOf<UUID, Int>()
    private val lock = Any()

    override fun add(tag: String?, entry: T) {
        return synchronized(lock) {
            if (uuidIndex.containsKey(entry.uuid)) {
                throw DuplicateUUIDException(entry.uuid)
            }
            entries.add(InMemoryRepositoryEntry(entry, tag))
            uuidIndex[entry.uuid] = entries.lastIndex
        }
    }

    override fun get(uuid: UUID): RepositoryEntry<T>? {
        return synchronized(lock) {
            uuidIndex[uuid]?.let {
                entries[it]
            }
        }
    }

    override fun get(tag: String?): List<RepositoryEntry<T>> {
        return getMatching {
            it.tag == tag
        }
    }

    override fun getMatching(predicate: (RepositoryEntry<T>) -> Boolean): List<RepositoryEntry<T>> {
        return synchronized(lock) {
            entries.filter(predicate)
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
