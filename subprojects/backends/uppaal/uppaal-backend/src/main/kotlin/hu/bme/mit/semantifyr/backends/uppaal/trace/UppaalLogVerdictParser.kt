/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.trace

private val OUT_OF_RANGE_ASSIGNMENT = Regex("Assignment of value '(-?\\d+)' to variable '([^']+)' is out of range")

class UppaalOutOfRangeAssignment(
    val variable: String,
    val value: String,
)

class UppaalLogVerdictParser {

    fun parse(lines: Sequence<String>): UppaalVerdict? {
        return lines.firstNotNullOfOrNull {
            detectInLine(it.trim())
        }
    }

    fun parseOutOfRangeAssignment(errorLines: Sequence<String>): UppaalOutOfRangeAssignment? {
        return errorLines.firstNotNullOfOrNull {
            OUT_OF_RANGE_ASSIGNMENT.find(it)
        }?.let {
            UppaalOutOfRangeAssignment(variable = it.groupValues[2], value = it.groupValues[1])
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
