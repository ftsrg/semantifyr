/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

class SpinLogVerdictParser {

    fun parse(lines: Sequence<String>): SpinVerdict? {
        var explicitFailure = false
        var explicitSuccess = false
        for (raw in lines) {
            val lower = raw.trim().lowercase()
            if (isFailureLine(lower)) {
                explicitFailure = true
            }
            if (isSuccessSummary(lower)) {
                explicitSuccess = true
            }
        }
        return when {
            explicitFailure -> SpinVerdict.False
            explicitSuccess -> SpinVerdict.True
            else -> null
        }
    }

    private fun isFailureLine(lowerLine: String): Boolean {
        return "violated" in lowerLine ||
            "acceptance cycle" in lowerLine ||
            "assertion violated" in lowerLine
    }

    private fun isSuccessSummary(lowerLine: String): Boolean {
        return "errors: 0" in lowerLine
    }
}
