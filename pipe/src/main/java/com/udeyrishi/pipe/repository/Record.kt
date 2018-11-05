/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

/**
 * Represents a unique item, and its metadata, as present in a `Repository`.
 *
 * Concrete `Repository` implementations will have their own `Record` implementations.
 */
interface Record<out T : Identifiable> : Identifiable {
    /**
     * The actual data item corresponding to the record.
     */
    val identifiableObject: T

    /**
     * The optional tag that was provided when this entry was added.
     */
    val tag: String?

    /**
     * A shortcut for getting the `identifiableObject`'s UUID.
     */
    override val uuid: UUID
        get() = identifiableObject.uuid
}
