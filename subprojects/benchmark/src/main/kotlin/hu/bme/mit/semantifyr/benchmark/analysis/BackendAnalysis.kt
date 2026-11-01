/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.benchmark.coordinateKey
import kotlin.time.DurationUnit

object BackendAnalysis {
    const val DATA_FILE = "backends.dat"
    const val CACTUS_FILE = "backends-cactus.dat"
    const val TABLE_FILE = "backends.tex"

    fun section(context: AnalysisContext): AnalysisSection? {
        val caseAxes = context.varyingAxes - context.engine - context.backend
        val cases = context.cells.groupBy {
            context.project(it.cell, caseAxes)
        }.mapValues { (_, cells) ->
            cells.associateBy { it.cell[context.backend].orEmpty() }
        }
        // a campaign that gives each model its own backend compares nothing
        if (cases.values.none { it.size > 1 }) {
            return null
        }
        val backends = context.values(context.backend)
        val agreement = cases.mapValues { (_, cells) ->
            val verdicts = cells.values.map { Obtained.of(it) }.filter { it.isDecisive }
            verdicts.takeIf { it.size >= 2 }?.let { it.distinct().size == 1 }
        }
        val common = cases.filterValues { cells ->
            backends.all { checkingTime(cells[it]) != null }
        }
        val data = Table(
            columns = caseAxes + backends.map { "${it}_s" } + listOf("agree"),
            rows = cases.map { (case, cells) ->
                case.values.toList() + backends.map { formatDecimal(checkingTime(cells[it])) } + listOf(
                    when (agreement[case]) {
                        true -> "1"
                        false -> "0"
                        null -> ""
                    },
                )
            },
        )
        val solved = backends.map { backend ->
            cases.values.mapNotNull { cells ->
                cells[backend]?.takeIf { it.asExpected != false }?.let { checkingTime(it) }
            }.sorted()
        }
        val cactus = Table(
            columns = listOf("solved") + backends.map { "${it}_s" },
            rows = (0 until (solved.maxOfOrNull { it.size } ?: 0)).map { index ->
                listOf((index + 1).toString()) + solved.map { formatDecimal(it.getOrNull(index)) }
            },
        )
        val table = Table(
            columns = listOf("Backend", "Cells", "Decided") + Judgement.entries.map { it.label } + listOf("Checking (s)", "Disagreements"),
            rows = backends.map { backend ->
                val cells = cases.values.mapNotNull { it[backend] }
                val judgements = cells.groupingBy { Judgement.of(it) }.eachCount()
                val disagreements = cases.count { (case, caseCells) ->
                    agreement[case] == false && caseCells[backend]?.let { Obtained.of(it).isDecisive } == true
                }
                listOf(
                    backend,
                    cells.size.toString(),
                    cells.count { Obtained.of(it).isDecisive }.toString(),
                ) + Judgement.entries.map { (judgements[it] ?: 0).toString() } + listOf(
                    formatDecimal(common.values.sumOf { checkingTime(it[backend]) ?: 0.0 }),
                    disagreements.toString(),
                )
            },
        )
        val decidedByTwo = agreement.values.count { it != null }
        val disagreeing = agreement.filterValues { it == false }.keys
        return AnalysisSection(
            title = "Backends (RQ3)",
            description = "Each backend's verdicts and model checking time per case. Of ${cases.size} cases, $decidedByTwo were decided by two or more backends and ${disagreeing.size} of these got different verdicts. The checking sum covers the ${common.size} cases every backend decided. In the data files a case a backend did not decide has no time, and `agree` is empty when fewer than two backends decided it; the cactus file sorts each backend's times of the cases it decided without a wrong verdict.",
            files = listOf(AnalysisFile(DATA_FILE, data.dat()), AnalysisFile(CACTUS_FILE, cactus.dat()), AnalysisFile(TABLE_FILE, table.latex())),
            tables = listOf(table),
            notes = disagreeing.map { case ->
                val verdicts = cases.getValue(case).entries.joinToString(", ") { (backend, cell) -> "$backend ${Obtained.of(cell).label}" }
                "Disagreement: `${coordinateKey(case)}`: $verdicts."
            },
        )
    }

    private fun checkingTime(cell: CellSummary?): Double? {
        if (cell == null || !Obtained.of(cell).isDecisive) {
            return null
        }
        return cell.backendTime?.median?.toDouble(DurationUnit.SECONDS)
    }
}
