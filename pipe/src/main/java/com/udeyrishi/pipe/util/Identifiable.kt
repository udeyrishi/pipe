/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.util

import java.util.UUID

/**
 * Represents an object that can be uniquely identified by its UUID.
 */
interface Identifiable {
    val uuid: UUID
}
