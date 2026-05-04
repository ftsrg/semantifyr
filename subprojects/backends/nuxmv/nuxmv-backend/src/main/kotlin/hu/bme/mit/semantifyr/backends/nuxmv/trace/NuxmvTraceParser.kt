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
        var inTrace = false
        var inState = false
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
                        states += NuxmvTraceState(running.toMap())
                    }
                    pendingState = true
                    inState = true
                }
                line.startsWith("-> Input:") -> {
                    if (pendingState) {
                        states += NuxmvTraceState(running.toMap())
                        pendingState = false
                    }
                    inState = false
                }
                line.startsWith("--") || line.isEmpty() -> {
                    // separator / comment line
                }
                line.startsWith("Trace ") -> {
                    // start of a follow-up trace block; commit pending and continue
                    if (pendingState) {
                        states += NuxmvTraceState(running.toMap())
                        pendingState = false
                    }
                }
                inState -> {
                    val (name, value) = splitAssignment(line) ?: continue
                    running[name] = value
                }
                else -> {
                    // input section line - ignore aux ivars
                }
            }
        }
        if (pendingState) {
            states += NuxmvTraceState(running.toMap())
        }
        if (states.isEmpty()) {
            return null
        }
        return NuxmvTrace(states)
    }

    private fun splitAssignment(line: String): Pair<String, String>? {
        val idx = line.indexOf(" = ")
        if (idx < 0) {
            return null
        }

        val name = line.substring(0, idx).trim()
        val value = line.substring(idx + 3).trim()
        if (name.isEmpty() || value.isEmpty()) {
            return null
        }

        return name to value
    }
}
