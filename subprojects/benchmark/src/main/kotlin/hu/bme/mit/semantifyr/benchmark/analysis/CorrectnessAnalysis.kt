/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.coordinateKey

enum class Judgement(
    val label: String,
) {
    AsExpected("As expected"),
    Wrong("Wrong"),
    Unjudged("Unjudged"),
    Timeout("Timeout"),
    Inconclusive("Inconclusive"),
    NotSupported("Not supported"),
    Errored("Errored"),
    Unavailable("Unavailable"),
    ;

    companion object {
        // a wrong repetition outweighs everything, an outcome that is not completed outweighs a correct repetition
        fun of(summary: CellSummary): Judgement {
            if (summary.asExpected == false) {
                return Wrong
            }
            return when (summary.outcome) {
                Outcome.Unavailable -> Unavailable
                Outcome.Errored -> Errored
                Outcome.TimedOut -> Timeout
                Outcome.Completed -> when {
                    summary.asExpected == true -> AsExpected
                    summary.verdicts.any { it.isDecisive } -> Unjudged
                    summary.verdicts.isNotEmpty() && summary.verdicts.all { it == VerificationVerdict.NotSupported } -> NotSupported
                    else -> Inconclusive
                }
            }
        }
    }
}

object CorrectnessAnalysis {
    const val TABLE_FILE = "correctness.tex"
    const val MATRIX_FILE = "verdicts.tex"

    private val expectations = listOf(VerificationVerdict.Passed, VerificationVerdict.Failed, null)

    fun section(context: AnalysisContext): AnalysisSection {
        val byModel = context.cells.groupBy {
            it.cell[context.model].orEmpty()
        }
        val table = Table(
            columns = listOf("Model", "Cells", "Expected passed", "Expected failed", "No expectation") + Judgement.entries.map { it.label },
            rows = byModel.map { (model, cells) ->
                listOf(model) + counts(cells)
            },
            totals = listOf(listOf("Total") + counts(context.cells)),
        )
        val matrix = Table(
            columns = listOf("Expected") + Obtained.entries.map { it.title } + listOf("Total"),
            rows = expectations.map { expected ->
                val cells = context.cells.filter { it.expected == expected }
                val obtained = cells.groupingBy { Obtained.of(it) }.eachCount()
                listOf(expectationLabel(expected)) + Obtained.entries.map { (obtained[it] ?: 0).toString() } + listOf(cells.size.toString())
            },
        )
        val wrong = context.cells.filter {
            it.asExpected == false
        }.map {
            "Wrong: `${coordinateKey(it.cell)}` expected ${it.expected}, obtained ${it.verdicts.joinToString(", ")}."
        }
        return AnalysisSection(
            title = "Correctness (RQ1)",
            description = "Every cell's expected verdict against the verdicts of its measured repetitions. A cell is wrong when any repetition gives a decisive verdict other than the expected one, and is counted by its outcome when that is not completed; unjudged cells gave a decisive verdict where none is expected.",
            files = listOf(AnalysisFile(TABLE_FILE, table.latex()), AnalysisFile(MATRIX_FILE, matrix.latex())),
            tables = listOf(table, matrix),
            notes = wrong.ifEmpty { listOf("No cell is wrong.") },
        )
    }

    private fun counts(cells: List<CellSummary>): List<String> {
        val judgements = cells.groupingBy { Judgement.of(it) }.eachCount()
        return listOf(
            cells.size,
            cells.count { it.expected == VerificationVerdict.Passed },
            cells.count { it.expected == VerificationVerdict.Failed },
            cells.count { it.expected == null },
        ).map { it.toString() } + Judgement.entries.map {
            (judgements[it] ?: 0).toString()
        }
    }

    private fun expectationLabel(expected: VerificationVerdict?): String {
        return when (expected) {
            VerificationVerdict.Passed -> "Passed"
            VerificationVerdict.Failed -> "Failed"
            else -> "None"
        }
    }
}
