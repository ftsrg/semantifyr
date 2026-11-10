/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

private val SOURCE_ERROR = Regex("^(TYPE ERROR )?file .+: line \\d+ ?: ")

class NuxmvLogVerdictParser {

    fun parse(lines: Sequence<String>): NuxmvVerdict? {
        var inBlock = false
        for (raw in lines) {
            val lower = raw.lowercase()
            if (opensBlock(lower)) {
                detectInLine(lower)?.let {
                    return it
                }
                inBlock = true
                continue
            }
            if (inBlock) {
                detectInLine(lower)?.let {
                    return it
                }
            }
        }
        return null
    }

    fun rejection(lines: Sequence<String>): String? {
        val messages = lines.map {
            it.trim()
        }.filter {
            isRejectionLine(it)
        }.toList()
        if (messages.isEmpty()) {
            return null
        }
        return messages.joinToString("; ")
    }

    private fun isRejectionLine(line: String): Boolean {
        return SOURCE_ERROR.containsMatchIn(line) ||
            line.startsWith("ERROR: ")
    }

    private fun opensBlock(lowerLine: String): Boolean {
        val trimmed = lowerLine.trimStart()
        return trimmed.startsWith("-- invariant") || trimmed.startsWith("-- specification")
    }

    private fun detectInLine(lowerLine: String): NuxmvVerdict? {
        val trimmed = lowerLine.trimEnd()
        return when {
            trimmed.endsWith("is true") -> NuxmvVerdict.True
            trimmed.endsWith("is false") -> NuxmvVerdict.False
            else -> null
        }
    }
}
