/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.util

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal fun Throwable.stackTraceToString(): String {
    return ByteArrayOutputStream().use {
        PrintStream(it).use(::printStackTrace)
        it.toString("UTF-8")
    }
}

internal fun Throwable.detailedToString(): String {
    var level = 0
    val sb = StringBuilder()
    var cause: Throwable? = this

    while (cause != null) {
        if (level++ > 0) {
            sb.append("\nCaused by: ")
        }
        sb.append(cause)
        cause = cause.cause
    }

    return sb.toString()
}

internal inline fun <reified T : Throwable, R> repeatUntilSucceeds(action: () -> R): R {
    while (true) {
        try {
            return action()
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }
}
