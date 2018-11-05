/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.pipeline

import com.udeyrishi.pipe.util.Identifiable
import java.util.UUID

internal data class Passenger<T : Any>(val data: T, override val uuid: UUID, val createdAt: Long) : Comparable<Passenger<T>>, Identifiable {
    override fun compareTo(other: Passenger<T>): Int = createdAt.compareTo(other.createdAt)
    override fun toString(): String = data.toString()
}
