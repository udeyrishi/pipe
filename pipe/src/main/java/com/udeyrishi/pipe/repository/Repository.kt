/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.io.Closeable
import java.util.UUID

/**
 * An abstract read-only key-value store of [Identifiable] items.
 *
 * - The items are stored by unique UUID keys.
 * - Items can optionally also have tags. The lack of a tag is represented by a null tag.
 *     - More than 1 item can have the same tag. Use tags for grouping categories of items.
 */
interface Repository<out T : Identifiable> : Closeable {
    /**
     * The current size of the repository.
     */
    val size: Int

    /**
     * The current list of items in the repository.
     */
    val items: List<Record<T>>

    /**
     * Retrieves the [Record] with the specified [uuid].
     * Returns null if it's not found.
     */
    operator fun get(uuid: UUID): Record<T>?

    /**
     * Returns the list of [Record]s matching the specified [tag].
     */
    operator fun get(tag: String?): List<Record<T>>

    /**
     * Returns the list of [Record]s matching the predicate.
     */
    fun getMatching(predicate: (Record<T>) -> Boolean): List<Record<T>>
}

/**
 * Extends [Repository] by adding mutability to it. Implementations must be thread-safe.
 */
interface MutableRepository<T : Identifiable> : Repository<T> {
    /**
     * Adds the [entry] to the repository under the given [tag].
     * Uses the entry's [Identifiable.uuid] for indexing.
     *
     * @throws DuplicateUUIDException If a record with the same [UUID] already exists.
     */
    fun add(tag: String?, entry: T)

    /**
     * Removes the [Record]s matching the [predicate].
     */
    fun removeIf(predicate: (Record<T>) -> Boolean)

    /**
     * Clears everything from the repository.
     */
    fun clear()
}

/**
 * Thrown by [MutableRepository.add] if a second entry with an already used UUID is added.
 */
class DuplicateUUIDException(uuid: UUID) : Exception("$uuid has already been used.")
