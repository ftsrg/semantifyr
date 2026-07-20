/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

import java.io.File

class SpinTraceParser {

    private val stepHeader = Regex("^\\s*(\\d+):\\s*proc\\b")

    fun parse(replayFile: File): SpinTrace? {
        if (!replayFile.exists()) {
            return null
        }
        return replayFile.useLines { parseLines(it) }
    }

    private fun parseLines(lines: Sequence<String>): SpinTrace? {
        val states = mutableListOf<SpinTraceState>()
        val running = mutableMapOf<String, String>()
        var inStep = false
        var currentDepth: Int? = null

        var trailEnded = false

        for (raw in lines) {
            val trimmed = raw.trim()
            if (!trailEnded && (trimmed.startsWith("spin: trail ends") || trimmed.startsWith("#processes:"))) {
                if (inStep) {
                    states += SpinTraceState(running.toMap())
                    inStep = false
                }
                trailEnded = true
                continue
            }

            val match = stepHeader.find(raw)
            when {
                trailEnded -> {
                    // Drop everything after the trail-end marker - it's process termination noise.
                }
                match != null -> {
                    val depth = match.groupValues[1].toInt()
                    if (currentDepth != null && depth != currentDepth) {
                        states += SpinTraceState(running.toMap())
                    }
                    currentDepth = depth
                    inStep = true
                }
                inStep && trimmed.contains(" = ") && !ignored(trimmed) -> {
                    val (name, value) = splitAssignment(trimmed) ?: continue
                    running[name] = value
                }
                else -> {
                    // narration / ltl chatter / never-claim moves / blank lines - drop
                }
            }
        }
        if (inStep) {
            states += SpinTraceState(running.toMap())
        }
        if (states.isEmpty()) {
            return null
        }
        return SpinTrace(states)
    }

    private fun ignored(line: String): Boolean {
        return line.startsWith("Never claim moves to") ||
            line.contains("processes created") ||
            line.endsWith(" terminates") ||
            line.startsWith("queue ") ||
            line.startsWith("ltl ") ||
            line.startsWith("starting claim") ||
            line.startsWith("spin:")
    }

    private fun splitAssignment(line: String): Pair<String, String>? {
        val index = line.indexOf(" = ")
        if (index < 0) {
            return null
        }
        val name = line.substring(0, index).trim()
        val value = line.substring(index + 3).trim()
        if (name.isEmpty() || value.isEmpty()) {
            return null
        }
        return name to value
    }
}
