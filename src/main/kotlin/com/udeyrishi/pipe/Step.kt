/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
typealias Step<T> = suspend (input: T) -> T