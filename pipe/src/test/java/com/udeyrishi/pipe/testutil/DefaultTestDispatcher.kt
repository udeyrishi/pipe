/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.testutil

import com.udeyrishi.pipe.PipelineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.fail

object DefaultTestDispatcher : PipelineDispatcher {
    override val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onInternalPipeError(throwable: Throwable) {
        fail("Test failed due to unexpected exception: $throwable")
    }
}
