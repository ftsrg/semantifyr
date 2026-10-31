/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.DurationUnit

object ResultsTable {
    private const val MAX_AXIS_VALUES = 40

    fun csv(summaries: List<CellSummary>): String {
        val axes = summaries.flatMap { it.cell.keys }.distinct()
        val extensionColumns = summaries.flatMap { it.extensions.keys }.distinct()
        val header = axes + listOf(
            "outcome",
            "verdict",
            "expected",
            "as_expected",
            "falsification",
            "decided_by",
            "repetitions",
        ) + statisticColumns("wall") + statisticColumns("cpu") + statisticColumns("backend_cpu") +
            statisticColumns("compilation") + statisticColumns("optimization") + statisticColumns("backend") +
            sizeColumns("input") + sizeColumns("inlined") + sizeColumns("flattened") + extensionColumns
        val rows = summaries.map { summary ->
            axes.map {
                summary.cell[it].orEmpty()
            } + listOf(
                summary.outcome.name,
                summary.verdicts.distinct().joinToString("|"),
                summary.expected?.name.orEmpty(),
                summary.asExpected?.toString().orEmpty(),
                summary.falsification.toString(),
                summary.decidedBy.joinToString("|"),
                summary.repetitions.toString(),
            ) + statisticValues(summary.wallTime) + statisticValues(summary.cpuTime) + statisticValues(summary.backendCpuTime) +
                statisticValues(summary.compilationTime) + statisticValues(summary.optimizationTime) + statisticValues(summary.backendTime) +
                sizeValues(summary.inputSize) + sizeValues(summary.inlinedSize) + sizeValues(summary.flattenedSize) +
                extensionColumns.map {
                    extensionValue(summary.extensions[it])
                }
        }
        return (listOf(header) + rows).joinToString("") { row ->
            row.joinToString(",") {
                csvField(it)
            } + "\n"
        }
    }

    fun markdown(campaign: String, summaries: List<CellSummary>): String {
        val axes = summaries.flatMap { it.cell.keys }.distinct()
        val sections = axes.mapNotNull { axis ->
            val groups = summaries.groupBy {
                it.cell[axis].orEmpty()
            }
            if (groups.size > MAX_AXIS_VALUES) {
                return@mapNotNull null
            }
            val rows = groups.map { (value, group) ->
                listOf(value) + aggregate(group)
            }
            "### By $axis\n\n${markdownTable(listOf(axis) + AGGREGATE_COLUMNS, rows)}"
        }
        val total = markdownTable(AGGREGATE_COLUMNS, listOf(aggregate(summaries)))
        return "## Campaign $campaign\n\n$total\n${sections.joinToString("\n")}"
    }

    private val AGGREGATE_COLUMNS = listOf(
        "cells",
        "completed",
        "timed out",
        "errored",
        "unavailable",
        "as expected",
        "wrong",
        "wall total (s)",
        "wall median (s)",
        "spread median (s)",
    )

    private fun aggregate(group: List<CellSummary>): List<String> {
        val medians = group.mapNotNull { it.wallTime?.median }
        val spreads = group.mapNotNull { it.wallTime?.spread }
        return listOf(
            group.size.toString(),
            group.count { it.outcome == Outcome.Completed }.toString(),
            group.count { it.outcome == Outcome.TimedOut }.toString(),
            group.count { it.outcome == Outcome.Errored }.toString(),
            group.count { it.outcome == Outcome.Unavailable }.toString(),
            group.count { it.asExpected == true }.toString(),
            group.count { it.asExpected == false }.toString(),
            seconds(medians.fold(Duration.ZERO) { total, median -> total + median }),
            Statistic.of(medians)?.median?.let {
                seconds(it)
            }.orEmpty(),
            Statistic.of(spreads)?.median?.let {
                seconds(it)
            }.orEmpty(),
        )
    }

    private fun markdownTable(header: List<String>, rows: List<List<String>>): String {
        val lines = listOf(header, header.map { "---" }) + rows
        return lines.joinToString("") { row ->
            row.joinToString(" | ", prefix = "| ", postfix = " |\n")
        }
    }

    private fun statisticColumns(name: String): List<String> {
        return listOf("${name}_median_s", "${name}_min_s", "${name}_max_s")
    }

    private fun statisticValues(statistic: Statistic?): List<String> {
        if (statistic == null) {
            return listOf("", "", "")
        }
        return listOf(seconds(statistic.median), seconds(statistic.minimum), seconds(statistic.maximum))
    }

    private fun sizeColumns(level: String): List<String> {
        return listOf("${level}_variables", "${level}_operations", "${level}_expressions")
    }

    private fun sizeValues(size: ModelSize?): List<String> {
        if (size == null) {
            return listOf("", "", "")
        }
        return listOf(size.variables.toString(), size.operations.toString(), size.expressions.toString())
    }

    private fun extensionValue(value: String?): String {
        if (value == null) {
            return ""
        }
        return Duration.parseIsoStringOrNull(value)?.let {
            seconds(it)
        } ?: value
    }

    private fun seconds(duration: Duration): String {
        return String.format(Locale.ROOT, "%.3f", duration.toDouble(DurationUnit.SECONDS))
    }

    private fun csvField(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
