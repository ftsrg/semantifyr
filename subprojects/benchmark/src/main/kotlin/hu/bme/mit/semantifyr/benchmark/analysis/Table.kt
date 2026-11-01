/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit

class Table(
    val columns: List<String>,
    val rows: List<List<String>>,
    val totals: List<List<String>> = emptyList(),
) {
    init {
        require((rows + totals).all { it.size == columns.size }) { "Every row needs ${columns.size} values" }
    }

    // pgfplots reads whitespace-separated columns and skips nan values
    fun dat(): String {
        return (listOf(columns) + rows).joinToString("") { row ->
            row.joinToString(" ") {
                it.replace(Regex("\\s"), "_").ifEmpty { "nan" }
            } + "\n"
        }
    }

    fun latex(): String {
        val alignment = columns.indices.joinToString("") { index ->
            if ((rows + totals).all { isNumeric(it[index]) }) {
                "r"
            } else {
                "l"
            }
        }
        val builder = StringBuilder()
        builder.append("\\begin{tabular}{$alignment}\n\\toprule\n")
        builder.append(latexRow(columns))
        builder.append("\\midrule\n")
        rows.forEach {
            builder.append(latexRow(it))
        }
        if (totals.isNotEmpty()) {
            builder.append("\\midrule\n")
            totals.forEach {
                builder.append(latexRow(it))
            }
        }
        builder.append("\\bottomrule\n\\end{tabular}\n")
        return builder.toString()
    }

    fun markdown(): String {
        val lines = listOf(columns, columns.map { "---" }) + rows + totals
        return lines.joinToString("") { row ->
            row.joinToString(" | ", prefix = "| ", postfix = " |\n") {
                it.replace("|", "\\|")
            }
        }
    }

    private fun latexRow(row: List<String>): String {
        return row.joinToString(" & ", postfix = " \\\\\n") {
            escapeLatex(it)
        }
    }

    private fun isNumeric(value: String): Boolean {
        return value.isEmpty() || value.toDoubleOrNull() != null
    }

    private fun escapeLatex(value: String): String {
        return value.replace(Regex("[_&%\$#{}]")) {
            "\\${it.value}"
        }
    }
}

fun formatSeconds(duration: Duration?): String {
    return duration?.let {
        formatDecimal(it.toDouble(DurationUnit.SECONDS))
    }.orEmpty()
}

fun formatDecimal(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) {
        return ""
    }
    return String.format(Locale.ROOT, "%.3f", value)
}

fun formatRatio(value: Double?, baseline: Double?): String {
    if (value == null || baseline == null || baseline == 0.0) {
        return ""
    }
    return formatDecimal(value / baseline)
}
