/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.io.Closeable
import java.util.UUID

interface Repository<out T : Identifiable> : Closeable {
    operator fun get(uuid: UUID): RepositoryEntry<T>?
    operator fun get(tag: String?): List<RepositoryEntry<T>>
    fun getMatching(predicate: (RepositoryEntry<T>) -> Boolean): List<RepositoryEntry<T>>
}

interface MutableRepository<T : Identifiable> : Repository<T> {
    fun add(tag: String?, entry: T)
    fun removeIf(predicate: (RepositoryEntry<T>) -> Boolean)
}

class DuplicateUUIDException(uuid: UUID) : Exception("$uuid has already been used.")
