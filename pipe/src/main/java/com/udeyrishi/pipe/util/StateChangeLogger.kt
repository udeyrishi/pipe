package com.udeyrishi.pipe.util

import com.udeyrishi.pipe.state.State
import com.udeyrishi.pipe.state.StateChangeListener
import java.io.OutputStreamWriter
import java.util.UUID

class StateChangeLogger(private val pipelineName: String, private val outputStream: OutputStreamWriter) : StateChangeListener {
    override fun onStateChanged(uuid: UUID, previousState: State, newState: State) {
        outputStream.write("$pipelineName: Pipeline passenger $uuid transitioned from state $previousState to $newState.")
    }
}