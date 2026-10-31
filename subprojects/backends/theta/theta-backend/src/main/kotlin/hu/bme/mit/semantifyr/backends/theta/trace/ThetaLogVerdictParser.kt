/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.trace

class ThetaLogVerdictParser {

    fun parse(lines: Sequence<String>): ThetaVerdict? {
        return lines.firstNotNullOfOrNull {
            detectInLine(it)
        }
    }

    private fun detectInLine(line: String): ThetaVerdict? {
        return when {
            line.contains("SafetyResult Unsafe") -> ThetaVerdict.Unsafe
            line.contains("SafetyResult Safe") -> ThetaVerdict.Safe
            else -> null
        }
    }
}
