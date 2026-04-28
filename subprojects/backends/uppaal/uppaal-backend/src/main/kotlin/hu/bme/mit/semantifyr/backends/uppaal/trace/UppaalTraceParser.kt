/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

import java.io.File

class UppaalTraceParser {

    fun parse(logFile: File): UppaalTrace? {
        if (!logFile.exists()) {
            return null
        }
        return logFile.useLines { parseLines(it) }
    }

    private fun parseLines(lines: Sequence<String>): UppaalTrace? {
        val states = mutableListOf<UppaalTraceState>()
        var inState = false
        var awaitingLocations = false
        var locations = mutableListOf<String>()
        var values = mutableMapOf<String, String>()

        for (raw in lines) {
            val line = raw.trim()
            when {
                line == "State:" -> {
                    if (inState) {
                        states += UppaalTraceState(locations.toList(), values.toMap())
                    }
                    inState = true
                    awaitingLocations = true
                    locations = mutableListOf()
                    values = mutableMapOf()
                }
                line.startsWith("Transition:") || line.startsWith("Delay:") -> {
                    if (inState) {
                        states += UppaalTraceState(locations.toList(), values.toMap())
                        inState = false
                        locations = mutableListOf()
                        values = mutableMapOf()
                    }
                }
                line.isEmpty() -> {
                    // blank line; remain in current section
                }
                inState && awaitingLocations -> {
                    // The locations line: `( proc.locA proc.locB ... )`. Strip the parens and
                    // keep the dotted location identifiers so the witness extractor can pick
                    // OXSTS-level boundary states.
                    locations += line.removePrefix("(").removeSuffix(")").trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                    awaitingLocations = false
                }
                inState -> {
                    line.split(' ').forEach { token ->
                        val (name, value) = splitAssignment(token) ?: return@forEach
                        values[name] = value
                    }
                }
            }
        }
        if (inState) {
            states += UppaalTraceState(locations.toList(), values.toMap())
        }
        if (states.isEmpty()) {
            return null
        }
        return UppaalTrace(states)
    }

    private fun splitAssignment(token: String): Pair<String, String>? {
        val idx = token.indexOf('=')
        if (idx <= 0 || idx == token.length - 1) return null
        val name = token.substring(0, idx).trim()
        val value = token.substring(idx + 1).trim()
        if (name.isEmpty() || value.isEmpty()) return null
        return name to value
    }
}
