/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

import java.io.File

class NuxmvTraceParser {

    fun parse(logFile: File): NuxmvTrace? {
        if (!logFile.exists()) {
            return null
        }
        return logFile.useLines { parseLines(it) }
    }

    private fun parseLines(lines: Sequence<String>): NuxmvTrace? {
        val states = mutableListOf<NuxmvTraceState>()
        val running = mutableMapOf<String, String>()
        val runningInputs = mutableMapOf<String, String>()
        var stateInputs = emptyMap<String, String>()
        var inTrace = false
        var inState = false
        var inInput = false
        var pendingState = false

        for (raw in lines) {
            val line = raw.trim()
            if (!inTrace) {
                if (line.startsWith("Trace Type:")) {
                    inTrace = true
                }
                continue
            }

            when {
                line.startsWith("-> State:") -> {
                    if (pendingState) {
                        states += NuxmvTraceState(running.toMap(), stateInputs)
                    }
                    stateInputs = if (inInput) {
                        runningInputs.toMap()
                    } else {
                        emptyMap()
                    }
                    pendingState = true
                    inState = true
                    inInput = false
                }
                line.startsWith("-> Input:") -> {
                    if (pendingState) {
                        states += NuxmvTraceState(running.toMap(), stateInputs)
                        pendingState = false
                    }
                    inState = false
                    inInput = true
                }
                line.startsWith("--") || line.isEmpty() -> {
                    // separator / comment line
                }
                line.startsWith("Trace ") -> {
                    // start of a follow-up trace block; commit pending and continue
                    if (pendingState) {
                        states += NuxmvTraceState(running.toMap(), stateInputs)
                        pendingState = false
                    }
                    inInput = false
                }
                inState -> {
                    val (name, value) = splitAssignment(line) ?: continue
                    running[name] = value
                }
                inInput -> {
                    val (name, value) = splitAssignment(line) ?: continue
                    runningInputs[name] = value
                }
            }
        }
        if (pendingState) {
            states += NuxmvTraceState(running.toMap(), stateInputs)
        }
        if (states.isEmpty()) {
            return null
        }
        return NuxmvTrace(states)
    }

    private fun splitAssignment(line: String): Assignment? {
        val index = line.indexOf(" = ")
        if (index < 0) {
            return null
        }

        val name = line.substring(0, index).trim()
        val value = line.substring(index + 3).trim()
        if (name.isEmpty() || value.isEmpty()) {
            return null
        }

        return Assignment(name, value)
    }

    private data class Assignment(
        val name: String,
        val value: String,
    )
}
