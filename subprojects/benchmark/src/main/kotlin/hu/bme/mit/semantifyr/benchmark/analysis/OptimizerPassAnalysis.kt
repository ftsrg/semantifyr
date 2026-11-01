/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import kotlin.time.DurationUnit

class LevelRow(
    val level: String,
    val variables: Int?,
    val operations: Int?,
    val expressions: Int?,
    val unfolding: Double?,
    val checking: Double?,
    val passTime: Double?,
    val firings: Int?,
    val timeouts: Int,
    val errors: Int,
    val wrong: Int,
)

object OptimizerPassAnalysis {
    const val DATA_FILE = "optimizer-passes.dat"
    const val TABLE_FILE = "optimizer-passes.tex"

    private const val TOTAL = "total"
    private const val MAX_MATRIX_GROUPS = 60

    fun section(context: AnalysisContext): AnalysisSection? {
        val baselineLevel = OptimizationLevel.All.name
        if (context.levels.size < 2 || baselineLevel !in context.levels) {
            return null
        }
        val groupAxes = context.varyingAxes - context.case - context.optimization
        val groups = context.cells.groupBy {
            context.project(it.cell, groupAxes)
        }.mapValues { (_, cells) ->
            levelRows(context, cells)
        }
        val totals = levelRows(context, context.cells)
        val data = Table(
            columns = groupAxes + listOf(
                "level",
                "variables",
                "operations",
                "expressions",
                "variables_ratio",
                "expressions_ratio",
                "unfolding_s",
                "unfolding_ratio",
                "checking_s",
                "checking_ratio",
                "pass_s",
                "firings",
                "timeouts",
                "errors",
                "wrong",
            ),
            rows = groups.flatMap { (group, rows) ->
                rows.map { group.values.toList() + values(it, baseline(rows)) }
            } + totals.map { row ->
                groupAxes.map { TOTAL } + values(row, baseline(totals))
            },
        )
        val table = Table(
            columns = listOf(
                "Level",
                "Variables",
                "Operations",
                "Expressions",
                "Var. ratio",
                "Expr. ratio",
                "Unfolding (s)",
                "Unf. ratio",
                "Checking (s)",
                "Chk. ratio",
                "Pass (s)",
                "Firings",
                "Timeouts",
                "Errors",
                "Wrong",
            ),
            rows = totals.map {
                values(it, baseline(totals))
            },
        )
        val matrices = if (groups.size in 2..MAX_MATRIX_GROUPS) {
            listOf(
                matrix(context, groupAxes, groups) { row, baseline -> formatRatio(row.expressions?.toDouble(), baseline.expressions?.toDouble()) },
                matrix(context, groupAxes, groups) { row, baseline -> formatRatio(row.checking, baseline.checking) },
            )
        } else {
            emptyList()
        }
        val description = "Each level disables the optional passes it names (`none` disables all of them) and is compared with `all`. Sizes are the flattened model's, summed over the cases sized at every level; times are sums of medians over the cases completed at every level, so a case that timed out at any level is left out of every level's sum and counted in that level's timeouts. Pass time and firings are those of the disabled passes, measured under `all`. The table sums every group; the data file also has each group."
        return AnalysisSection(
            title = "Optimizer passes (RQ2)",
            description = if (matrices.isEmpty()) {
                description
            } else {
                "$description The two matrices give, per group and level, the flattened expressions and the model checking time relative to `all`."
            },
            files = listOf(AnalysisFile(DATA_FILE, data.dat()), AnalysisFile(TABLE_FILE, table.latex())),
            tables = listOf(table) + matrices,
        )
    }

    private fun levelRows(context: AnalysisContext, cells: List<CellSummary>): List<LevelRow> {
        val caseAxes = context.axes - context.optimization
        val cases = cells.groupBy {
            context.project(it.cell, caseAxes)
        }.values.map { caseCells ->
            caseCells.associateBy { it.cell[context.optimization].orEmpty() }
        }
        val sized = cases.filter { levels ->
            context.levels.all { levels[it]?.flattenedSize != null }
        }
        val timed = cases.filter { levels ->
            context.levels.all { level ->
                val cell = levels[level]
                cell != null && cell.outcome == Outcome.Completed && cell.compilationTime != null && cell.backendTime != null
            }
        }
        val baselineCells = cases.mapNotNull { it[OptimizationLevel.All.name] }.filter { it.passes.isNotEmpty() }
        return context.levels.map { level ->
            val levelCells = cases.mapNotNull { it[level] }
            val sizes = sized.mapNotNull { it[level]?.flattenedSize }
            // the pass optimizer names a pass's statistics after its class
            val disabled = OptimizationLevel.byName(level)?.disabled?.map { "${it.name}Pass" }
            val passes = disabled?.takeIf { names ->
                baselineCells.all { cell -> names.all { it in cell.passes } }
            }?.let { names ->
                baselineCells.flatMap { cell -> names.mapNotNull { cell.passes[it] } }
            }
            LevelRow(
                level = level,
                variables = sizes.takeIf { it.isNotEmpty() }?.sumOf { it.variables },
                operations = sizes.takeIf { it.isNotEmpty() }?.sumOf { it.operations },
                expressions = sizes.takeIf { it.isNotEmpty() }?.sumOf { it.expressions },
                unfolding = timed.takeIf { it.isNotEmpty() }?.sumOf { it[level]?.compilationTime?.median?.toDouble(DurationUnit.SECONDS) ?: 0.0 },
                checking = timed.takeIf { it.isNotEmpty() }?.sumOf { it[level]?.backendTime?.median?.toDouble(DurationUnit.SECONDS) ?: 0.0 },
                passTime = passes?.sumOf { it.time.median.toDouble(DurationUnit.SECONDS) },
                firings = passes?.sumOf { it.firings },
                timeouts = levelCells.count { it.outcome == Outcome.TimedOut },
                errors = levelCells.count { it.outcome == Outcome.Errored },
                wrong = levelCells.count { it.asExpected == false },
            )
        }
    }

    private fun baseline(rows: List<LevelRow>): LevelRow {
        return rows.first {
            it.level == OptimizationLevel.All.name
        }
    }

    private fun values(row: LevelRow, baseline: LevelRow): List<String> {
        return listOf(
            row.level,
            row.variables?.toString().orEmpty(),
            row.operations?.toString().orEmpty(),
            row.expressions?.toString().orEmpty(),
            formatRatio(row.variables?.toDouble(), baseline.variables?.toDouble()),
            formatRatio(row.expressions?.toDouble(), baseline.expressions?.toDouble()),
            formatDecimal(row.unfolding),
            formatRatio(row.unfolding, baseline.unfolding),
            formatDecimal(row.checking),
            formatRatio(row.checking, baseline.checking),
            formatDecimal(row.passTime),
            row.firings?.toString().orEmpty(),
            row.timeouts.toString(),
            row.errors.toString(),
            row.wrong.toString(),
        )
    }

    private fun matrix(
        context: AnalysisContext,
        groupAxes: List<String>,
        groups: Map<Map<String, String>, List<LevelRow>>,
        value: (LevelRow, LevelRow) -> String,
    ): Table {
        return Table(
            columns = groupAxes.map { title(it) } + context.levels,
            rows = groups.map { (group, rows) ->
                group.values.toList() + rows.map { value(it, baseline(rows)) }
            },
        )
    }
}
