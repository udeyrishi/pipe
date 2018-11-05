/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

interface CountedBarrierController {
    val arrivalCount: Int

    fun getCapacity(): Int
    fun getErrorCount(): Int
    fun setCapacity(capacity: Int)
}

interface ManualBarrierController {
    fun lift()
}
