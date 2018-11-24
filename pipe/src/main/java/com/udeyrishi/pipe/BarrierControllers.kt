/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

/**
 * A "barrier" in pipe has roughly the same meaning as the standard CS Barrier(https://en.wikipedia.org/wiki/Barrier_(computer_science)).
 *
 * - It can be conceptualized as a check-point, where all parallel `Job`s _may_ stop before continuing.
 * - The condition controlling whether a given `Job` will stop at the barrier or not is dependent on the barrier's controller (see below).
 * - The continuation condition is also dependent on the barrier's controller.
 */

/**
 * This barrier controller models the textbook Barrier(https://en.wikipedia.org/wiki/Barrier_(computer_science)) for jobs.
 *
 * When this controller is used with a barrier:
 * - The barrier has a max capacity.
 * - All the jobs arriving at the barrier suspend until the arrival count == max capacity.
 * - The barrier is lifted the moment the last job arrives.
 * - You can optionally specify an `onBarrierLiftedAction` which can be used for group-processing
 *   the data in those jobs, as computed until the preceding step.
 *
 * Note that this controller is fault-tolerant in a sense that if one of the jobs fail, it won't keep
 * all the siblings waiting indefinitely. The barrier will be lifted when the arrival count == capacity - error count.
 * That will also be reflected in the args to the `onBarrierLiftedAction`.
 *
 *
 * TL;DR: use this controller when you need to start n jobs in parallel, group process them once they've all reached a certain step,
 * and allow them to continue independently afterwards. So an n -> 1 -> n shaped flow.
 */
interface CountedBarrierController {
    val arrivalCount: Int

    fun getCapacity(): Int
    fun getErrorCount(): Int

    /**
     * Updates the barrier's capacity.
     *
     * - If the arrivalCount is `n`, the new capacity must be >= n. Else, `IllegalStateException` would be thrown.
     * - If the new capacity == arrival count, the barrier will also be lifted.
     * - If more jobs arrive after the barrier have been lifted (i.e., arrivalCount had reached the capacity), `IllegalStateException` would be thrown.
     *
     * It's usually recommended to start with a really high number (Int.MAX_VALUE) as the capacity, and update once you conclusively
     * know what the arrival count is going to be.
     */
    fun setCapacity(capacity: Int)
}

/**
 * A simple barrier controller that lifts the barrier when `lift()` is called.
 *
 * Once `lift()` is called:
 * - All pending jobs will be lifted immediately.
 * - Any future arrivals will continue through the barrier without stopping at all.
 */
interface ManualBarrierController {
    fun lift()
}
