/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.steps

typealias Step<T> = suspend (input: T) -> T
