/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

/**
 * A simple in-memory [Record] to accompany the [InMemoryRepository].
 */
private data class InMemoryRecord<T : Identifiable>(override val identifiableObject: T, override val tag: String?) : Record<T>

/**
 * A simple, [HashMap] backed in-memory implementation of [MutableRepository].
 */
class InMemoryRepository<T : Identifiable> : MutableRepository<T> {
    private val entries = arrayListOf<InMemoryRecord<T>>()
    private val uuidIndex = hashMapOf<UUID, Int>()
    private val lock = Any()

    override val size: Int
        get() = entries.size

    override val items: List<Record<T>>
        get() = entries.map { it }

    override fun add(tag: String?, entry: T) {
        return synchronized(lock) {
            if (uuidIndex.containsKey(entry.uuid)) {
                throw DuplicateUUIDException(entry.uuid)
            }
            entries.add(InMemoryRecord(entry, tag))
            uuidIndex[entry.uuid] = entries.lastIndex
        }
    }

    override fun get(uuid: UUID): Record<T>? {
        return synchronized(lock) {
            uuidIndex[uuid]?.let {
                entries[it]
            }
        }
    }

    override fun get(tag: String?): List<Record<T>> {
        return getMatching {
            it.tag == tag
        }
    }

    override fun getMatching(predicate: (Record<T>) -> Boolean): List<Record<T>> {
        return synchronized(lock) {
            entries.filter(predicate)
        }
    }

    override fun removeIf(predicate: (Record<T>) -> Boolean) {
        synchronized(lock) {
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (predicate(item)) {
                    iterator.remove()
                    uuidIndex.remove(item.uuid)
                }
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            entries.clear()
            uuidIndex.clear()
        }
    }

    /**
     * No-op. In memory repository doesn't need to be closed.
     */
    override fun close() {}
}
