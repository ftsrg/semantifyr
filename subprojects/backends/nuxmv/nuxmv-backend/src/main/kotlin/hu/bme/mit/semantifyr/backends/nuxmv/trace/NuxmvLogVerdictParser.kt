/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.trace

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
