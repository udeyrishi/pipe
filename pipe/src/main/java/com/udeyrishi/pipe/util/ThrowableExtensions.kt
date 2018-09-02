package com.udeyrishi.pipe.util

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal fun Throwable.stackTraceToString(): String {
    return ByteArrayOutputStream().use {
        PrintStream(it).use(::printStackTrace)
        it.toString("UTF-8")
    }
}
