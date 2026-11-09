/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.trace

private const val MERGE_LIMIT = "merge requires more than 256 bups"
private val SPIN_ERROR = Regex("^spin: .*Error: ")
private val COMPILER_ERROR = Regex("^\\S+:\\d+(:\\d+)?: (fatal )?error: ")
private val VECTOR_SIZE_TOO_SMALL = Regex("VECTORSZ too small.*N>(\\d+)")

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

    fun exceedsMergeLimit(lines: Sequence<String>): Boolean {
        return lines.any {
            MERGE_LIMIT in it
        }
    }

    fun requiredVectorSize(lines: Sequence<String>): Int? {
        return lines.firstNotNullOfOrNull {
            VECTOR_SIZE_TOO_SMALL.find(it)
        }?.let {
            it.groupValues[1].toInt()
        }
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
        return (SPIN_ERROR.containsMatchIn(line) && MERGE_LIMIT !in line) ||
            COMPILER_ERROR.containsMatchIn(line) ||
            line.startsWith("pan: error")
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
