/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import com.udeyrishi.pipe.PipelineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

internal fun PipelineDispatcher.createEffectiveContext(): CoroutineContext {
    return coroutineDispatcher + CoroutineExceptionHandler { _, throwable ->
        onInternalPipeError(throwable)
    }
}
