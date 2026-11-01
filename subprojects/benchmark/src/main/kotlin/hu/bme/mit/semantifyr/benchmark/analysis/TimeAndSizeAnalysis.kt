/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.sources.SysMLMeasurement
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import kotlin.time.Duration
import kotlin.time.DurationUnit

object UnfoldingAnalysis {
    const val DATA_FILE = "unfolding.dat"
    const val TABLE_FILE = "unfolding.tex"

    private val transformationKey = "${SysMLMeasurement.extension.key}.transformationDuration"

    fun section(context: AnalysisContext): AnalysisSection {
        val data = Table(
            columns = context.baselineAxes + statisticColumns("unfolding") + statisticColumns("checking") + listOf("verdict"),
            rows = context.baseline.filter {
                it.outcome == Outcome.Completed || it.outcome == Outcome.TimedOut
            }.map { summary ->
                context.baselineAxes.map { summary.cell[it].orEmpty() } +
                    statisticValues(summary.compilationTime) +
                    statisticValues(summary.backendTime) +
                    listOf(Obtained.of(summary).label)
            },
        )
        val groupAxes = context.baselineAxes - context.case
        val groups = context.baseline.groupBy {
            context.project(it.cell, groupAxes)
        }
        val transformations = context.baseline.groupBy {
            it.cell[context.model].orEmpty()
        }.mapValues { (_, cells) ->
            cells.firstNotNullOfOrNull { cell ->
                cell.extensions[transformationKey]?.let { Duration.parseIsoStringOrNull(it) }
            }
        }
        val table = Table(
            columns = groupAxes.map { title(it) } + listOf("Cells", "Completed", "Timeouts", "Transformation (s)", "Unfolding (s)", "Checking (s)", "Unfolding share"),
            rows = groups.map { (group, cells) ->
                group.values.toList() + row(cells, transformations[group.getValue(context.model)])
            },
            totals = listOf(
                listOf("Total") + groupAxes.drop(1).map { "" } + row(context.baseline, transformations.values.filterNotNull().fold(Duration.ZERO) { total, time -> total + time }),
            ),
        )
        return AnalysisSection(
            title = "Unfolding and model checking (RQ2)",
            description = "Unfolding is the four compiler phases, from the OXSTS model to the flattened model; model checking is the backend tool's run, without translating its input or reading back a witness. The transformation is the SysML frontend's translation to OXSTS, once per model. Sums cover the completed cells; a timed-out cell is listed in the data file with its verdict and no times.",
            files = listOf(AnalysisFile(DATA_FILE, data.dat()), AnalysisFile(TABLE_FILE, table.latex())),
            tables = listOf(table),
        )
    }

    private fun row(cells: List<CellSummary>, transformation: Duration?): List<String> {
        val timed = cells.filter {
            it.outcome == Outcome.Completed
        }.mapNotNull { cell ->
            cell.compilationTime?.let { unfolding ->
                cell.backendTime?.let { checking -> unfolding.median to checking.median }
            }
        }
        val unfolding = timed.sumOf { it.first.toDouble(DurationUnit.SECONDS) }
        val checking = timed.sumOf { it.second.toDouble(DurationUnit.SECONDS) }
        return listOf(
            cells.size.toString(),
            timed.size.toString(),
            cells.count { it.outcome == Outcome.TimedOut }.toString(),
            formatSeconds(transformation),
            formatDecimal(unfolding),
            formatDecimal(checking),
            formatRatio(unfolding, unfolding + checking),
        )
    }
}

object SizeAnalysis {
    const val DATA_FILE = "sizes.dat"
    const val TABLE_FILE = "sizes.tex"

    private val levels = listOf("input", "inlined", "flattened")

    fun section(context: AnalysisContext): AnalysisSection? {
        // the compiled model depends on the model, the case and the optimization, never on the backend
        val keyAxes = context.baselineAxes - context.engine - context.backend
        val cells = context.baseline.filter {
            it.flattenedSize != null
        }.distinctBy {
            context.project(it.cell, keyAxes)
        }
        if (cells.isEmpty()) {
            return null
        }
        val data = Table(
            columns = keyAxes + levels.flatMap { level -> listOf("variables", "operations", "expressions").map { "${level}_$it" } },
            rows = cells.map { summary ->
                keyAxes.map { summary.cell[it].orEmpty() } + sizes(summary).flatMap { sizeValues(it) }
            },
        )
        val groupAxes = keyAxes - context.case
        val table = Table(
            columns = groupAxes.map { title(it) } + listOf("Cases") + levels.flatMap { level -> listOf("V", "O", "E").map { "${title(level)} $it" } },
            rows = cells.groupBy {
                context.project(it.cell, groupAxes)
            }.map { (group, groupCells) ->
                group.values.toList() + listOf(groupCells.size.toString()) + levels.indices.flatMap { level ->
                    val medians = groupCells.map { sizes(it)[level] }
                    listOf(
                        medianCount(medians.map { it.variables }),
                        medianCount(medians.map { it.operations }),
                        medianCount(medians.map { it.expressions }),
                    )
                }
            },
        )
        return AnalysisSection(
            title = "Model sizes (RQ2)",
            description = "Variables (V), operations (O) and expressions (E) of the OXSTS input model, the inlined model and the flattened model that reaches the backend. The input counts the model's own file without the libraries it imports, so a SysML model's input is its structure only; the inlined and flattened sizes are per case, and the table gives their medians over the cases.",
            files = listOf(AnalysisFile(DATA_FILE, data.dat()), AnalysisFile(TABLE_FILE, table.latex())),
            tables = listOf(table),
        )
    }

    private fun sizes(summary: CellSummary): List<ModelSize> {
        return listOf(summary.inputSize, summary.inlinedSize, summary.flattenedSize).map {
            it ?: ModelSize()
        }
    }

    private fun sizeValues(size: ModelSize): List<String> {
        return listOf(size.variables, size.operations, size.expressions).map { it.toString() }
    }

    private fun medianCount(values: List<Int>): String {
        val sorted = values.sorted()
        val median = (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2.0
        return if (median % 1.0 == 0.0) {
            median.toInt().toString()
        } else {
            median.toString()
        }
    }
}

fun statisticColumns(name: String): List<String> {
    return listOf("${name}_median_s", "${name}_min_s", "${name}_max_s")
}

fun statisticValues(statistic: Statistic?): List<String> {
    return listOf(formatSeconds(statistic?.median), formatSeconds(statistic?.minimum), formatSeconds(statistic?.maximum))
}

fun title(name: String): String {
    return name.replaceFirstChar { it.uppercase() }
}
