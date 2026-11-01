/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.BenchmarkRecord
import hu.bme.mit.semantifyr.benchmark.Outcome
import hu.bme.mit.semantifyr.benchmark.RunEnvironment
import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import hu.bme.mit.semantifyr.benchmark.verification.VerificationAxes
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class AnalysisFile(
    val name: String,
    val content: String,
)

class AnalysisSection(
    val title: String,
    val description: String,
    val files: List<AnalysisFile>,
    val tables: List<Table>,
    val notes: List<String> = emptyList(),
)

enum class Obtained(
    val label: String,
) {
    Passed("passed"),
    Failed("failed"),
    Inconclusive("inconclusive"),
    NotSupported("not-supported"),
    Mixed("mixed"),
    Timeout("timeout"),
    Errored("errored"),
    Unavailable("unavailable"),
    ;

    val isDecisive: Boolean
        get() = this == Passed || this == Failed

    val title: String
        get() = label.replaceFirstChar { it.uppercase() }.replace('-', ' ')

    companion object {
        fun of(summary: CellSummary): Obtained {
            return when (summary.outcome) {
                Outcome.Unavailable -> Unavailable
                Outcome.Errored -> Errored
                Outcome.TimedOut -> Timeout
                Outcome.Completed -> when (summary.verdict) {
                    VerificationVerdict.Passed -> Passed
                    VerificationVerdict.Failed -> Failed
                    VerificationVerdict.Inconclusive -> Inconclusive
                    VerificationVerdict.NotSupported -> NotSupported
                    else -> Mixed
                }
            }
        }
    }
}

class AnalysisContext(
    summaries: List<CellSummary>,
) {
    val model = VerificationAxes.model.name
    val case = VerificationAxes.case.name
    val engine = VerificationAxes.engine.name
    val backend = VerificationAxes.backendConfig.name
    val optimization = VerificationAxes.optimization.name

    val axes = summaries.flatMap { it.cell.keys }.distinct()

    val cells = summaries.sortedWith { left, right ->
        compareCells(left.cell, right.cell)
    }

    val varyingAxes = axes.filter { axis ->
        axis == model || axis == case || cells.map { it.cell[axis] }.distinct().size > 1
    }

    val levels = values(optimization)

    // a campaign over optimization levels reports its unfolding times and sizes at all
    val baseline = if (levels.size > 1 && OptimizationLevel.All.name in levels) {
        cells.filter { it.cell[optimization] == OptimizationLevel.All.name }
    } else {
        cells
    }

    val baselineAxes = varyingAxes.filter {
        it != optimization || baseline.size == cells.size
    }

    fun values(axis: String): List<String> {
        return cells.mapNotNull { it.cell[axis] }.distinct()
    }

    fun project(cell: Map<String, String>, axes: List<String>): Map<String, String> {
        return axes.associateWith {
            cell[it].orEmpty()
        }
    }

    private fun compareCells(left: Map<String, String>, right: Map<String, String>): Int {
        return axes.asSequence().map {
            compareAxisValues(it, left[it].orEmpty(), right[it].orEmpty())
        }.firstOrNull {
            it != 0
        } ?: 0
    }

    private fun compareAxisValues(
        axis: String,
        left: String,
        right: String,
    ): Int {
        if (axis == optimization) {
            return compareValuesBy(left, right, { levelIndex(it) }, { it })
        }
        return left.compareTo(right)
    }

    private fun levelIndex(name: String): Int {
        val index = OptimizationLevel.leaveOneOut.indexOfFirst {
            it.name == name
        }
        return if (index < 0) {
            Int.MAX_VALUE
        } else {
            index
        }
    }
}

class CampaignAnalysis(
    private val records: List<BenchmarkRecord>,
    private val environment: RunEnvironment?,
) {
    private val context = AnalysisContext(summarize(records))

    val sections: List<AnalysisSection> = listOfNotNull(
        CorrectnessAnalysis.section(context),
        UnfoldingAnalysis.section(context),
        SizeAnalysis.section(context),
        OptimizerPassAnalysis.section(context),
        BackendAnalysis.section(context),
    )

    fun markdown(): String {
        val campaigns = records.map { it.campaign }.distinct().joinToString(", ")
        val builder = StringBuilder("# Campaign $campaigns\n\n")
        metadata().forEach {
            builder.append("- $it\n")
        }
        sections.forEach { section ->
            builder.append("\n## ${section.title}\n\n${section.description}\n\n")
            builder.append("Files: ${section.files.joinToString(", ") { "`${it.name}`" }}.\n")
            section.tables.forEach {
                builder.append("\n${it.markdown()}")
            }
            if (section.notes.isNotEmpty()) {
                builder.append("\n")
                section.notes.forEach {
                    builder.append("- $it\n")
                }
            }
        }
        return builder.toString()
    }

    fun write(directory: Path) {
        directory.createDirectories()
        OUTPUT_FILES.forEach {
            directory.resolve(it).deleteIfExists()
        }
        sections.flatMap { it.files }.forEach {
            directory.resolve(it.name).writeText(it.content)
        }
        directory.resolve(MARKDOWN_FILE).writeText(markdown())
    }

    private fun metadata(): List<String> {
        val measured = records.count { !it.warmup }
        val warmups = records.count { it.warmup }
        val lines = mutableListOf("${context.cells.size} cells, $measured measured runs and $warmups warmup runs.")
        if (environment != null) {
            val runexec = if (environment.runexec) {
                "with runexec"
            } else {
                "without runexec"
            }
            val version = environment.version?.let { "Semantifyr $it, " }.orEmpty()
            lines += "Run ${environment.startedAt}: $version${environment.repetitions} repetitions after ${environment.warmups} warmups, timeout ${environment.timeout}, ${environment.processors} processors, ${environment.operatingSystem}, Java ${environment.java}, backends $runexec."
        }
        val loads = records.mapNotNull { it.measurement.loadAverage }.sorted()
        if (loads.isEmpty()) {
            lines += "Host load average not recorded."
        } else {
            val processors = environment?.processors?.let { " on $it processors" }.orEmpty()
            val median = (loads[(loads.size - 1) / 2] + loads[loads.size / 2]) / 2
            var load = "Host load average at cell start ${formatDecimal(loads.first())} to ${formatDecimal(loads.last())}, median ${formatDecimal(median)}$processors."
            if (environment != null && loads.last() > environment.processors / 2.0) {
                load += " The host was loaded, so these times are not comparable with a quiet host's."
            }
            lines += load
        }
        lines += "Times are medians over the measured repetitions, with minimum and maximum in the data files; sums add the medians of cells. A cell without a completed measured repetition has no times and is counted by its outcome."
        return lines
    }

    companion object {
        const val MARKDOWN_FILE = "analysis.md"

        val OUTPUT_FILES = listOf(
            MARKDOWN_FILE,
            CorrectnessAnalysis.TABLE_FILE,
            CorrectnessAnalysis.MATRIX_FILE,
            UnfoldingAnalysis.DATA_FILE,
            UnfoldingAnalysis.TABLE_FILE,
            SizeAnalysis.DATA_FILE,
            SizeAnalysis.TABLE_FILE,
            OptimizerPassAnalysis.DATA_FILE,
            OptimizerPassAnalysis.TABLE_FILE,
            BackendAnalysis.DATA_FILE,
            BackendAnalysis.CACTUS_FILE,
            BackendAnalysis.TABLE_FILE,
        )
    }
}
