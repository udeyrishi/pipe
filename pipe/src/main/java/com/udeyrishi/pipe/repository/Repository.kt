/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.io.Closeable
import java.util.UUID

/**
 * An abstract read-only key-value store of `Identifiable` items.
 *
 * - The items are stored by unique UUID keys.
 * - Items can optionally also have tags. The lack of a tag is represented by a `null` tag.
 *     - More than 1 item can have the same tag. Use tags for grouping categories of items.
 */
interface Repository<out T : Identifiable> : Closeable {
    operator fun get(uuid: UUID): Record<T>?
    operator fun get(tag: String?): List<Record<T>>
    fun getMatching(predicate: (Record<T>) -> Boolean): List<Record<T>>
}

/**
 * Extends `Repository` by adding mutability to it. Implementations must be thread-safe.
 */
interface MutableRepository<T : Identifiable> : Repository<T> {
    fun add(tag: String?, entry: T)
    fun removeIf(predicate: (Record<T>) -> Boolean)
}

/**
 * Thrown by `MutableRepository::add` if a second entry with an already used UUID is added.
 */
class DuplicateUUIDException(uuid: UUID) : Exception("$uuid has already been used.")
