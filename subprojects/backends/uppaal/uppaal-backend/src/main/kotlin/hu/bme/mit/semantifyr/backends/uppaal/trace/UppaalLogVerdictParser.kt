/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

class UppaalLogVerdictParser {

    fun parse(lines: Sequence<String>): UppaalVerdict? {
        return lines.firstNotNullOfOrNull {
            detectInLine(it.trim())
        }
    }

    private fun detectInLine(trimmedLine: String): UppaalVerdict? {
        return when {
            trimmedLine.contains("Formula is NOT satisfied", ignoreCase = true) -> UppaalVerdict.Unsatisfied
            trimmedLine.contains("Property is NOT satisfied", ignoreCase = true) -> UppaalVerdict.Unsatisfied
            trimmedLine.contains("Formula is satisfied", ignoreCase = true) -> UppaalVerdict.Satisfied
            trimmedLine.contains("Property is satisfied", ignoreCase = true) -> UppaalVerdict.Satisfied
            else -> null
        }
    }
}
