package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.Identifiable
import com.udeyrishi.pipe.Orchestrator
import java.io.Closeable
import java.util.UUID

interface Repository<T : Identifiable> : Closeable {
    operator fun get(uuid: UUID): RepositoryEntry<T>?
    operator fun get(tag: String?): List<RepositoryEntry<T>>
}

interface MutableRepository<T : Identifiable> : Repository<T> {
    fun add(tag: String?, orchestratorBuilder: (newUUID: UUID, position: Long) -> Orchestrator<T>): Orchestrator<T>
    fun prune(removeFailures: Boolean)
}