/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

/**
 * Represents an abstract input-output step that can be executed by the pipeline.
 *
 * It is essential that this function does not carry/modify any state in your project, since this
 * function will be reused across all the jobs in the pipeline. It may also be re-run for the same job
 * in case of failures (if allowed by the maxAttempts criterion).
 */
typealias Step<T> = suspend (input: T) -> T
