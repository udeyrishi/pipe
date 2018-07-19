package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.Identifiable
import com.udeyrishi.pipe.Orchestrator
import java.util.UUID

interface RepositoryEntry<T : Identifiable> : Identifiable {
    val orchestrator: Orchestrator<T>
    val tag: String?
    override val uuid: UUID
        get() = orchestrator.uuid
}