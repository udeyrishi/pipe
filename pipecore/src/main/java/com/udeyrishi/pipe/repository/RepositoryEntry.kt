package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import com.udeyrishi.pipe.Orchestrator
import java.util.UUID

interface RepositoryEntry<out T : Identifiable> : Identifiable {
    val orchestrator: Orchestrator<T>
    val tag: String?
    override val uuid: UUID
        get() = orchestrator.uuid
}