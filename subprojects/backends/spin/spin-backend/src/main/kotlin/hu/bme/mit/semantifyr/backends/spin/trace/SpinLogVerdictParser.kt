/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

data class SpinLogSummary(
    val violationFound: Boolean,
    val reportedNoErrors: Boolean,
    val exhaustiveSearch: Boolean,
    val executionProblem: Boolean,
) {
    fun toVerdict(): SpinVerdict? {
        return when {
            violationFound -> SpinVerdict.False
            reportedNoErrors && exhaustiveSearch && !executionProblem -> SpinVerdict.True
            else -> null
        }
    }
}

class SpinLogVerdictParser {

    fun parse(lines: Sequence<String>): SpinVerdict? {
        return summarize(lines).toVerdict()
    }

    fun summarize(lines: Sequence<String>): SpinLogSummary {
        var violationFound = false
        var reportedNoErrors = false
        var exhaustiveSearch = false
        var executionProblem = false
        for (raw in lines) {
            val lower = raw.trim().lowercase()
            if (isViolationLine(lower)) {
                violationFound = true
            }
            if (isSuccessSummary(lower)) {
                reportedNoErrors = true
            }
            if (isExhaustiveSearchLine(lower)) {
                exhaustiveSearch = true
            }
            if (isExecutionProblemLine(lower)) {
                executionProblem = true
            }
        }
        return SpinLogSummary(
            violationFound = violationFound,
            reportedNoErrors = reportedNoErrors,
            exhaustiveSearch = exhaustiveSearch,
            executionProblem = executionProblem,
        )
    }

    private fun isViolationLine(lowerLine: String): Boolean {
        return "violated" in lowerLine ||
            "acceptance cycle" in lowerLine ||
            "assertion violated" in lowerLine
    }

    private fun isSuccessSummary(lowerLine: String): Boolean {
        return "errors: 0" in lowerLine
    }

    private fun isExhaustiveSearchLine(lowerLine: String): Boolean {
        return "full statespace search" in lowerLine
    }

    private fun isExecutionProblemLine(lowerLine: String): Boolean {
        return lowerLine.startsWith("error:") ||
            "search not completed" in lowerLine ||
            "out of memory" in lowerLine
    }
}
