/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.Runexec
import hu.bme.mit.semantifyr.benchmark.analysis.CampaignAnalysis
import hu.bme.mit.semantifyr.benchmark.analysis.CellSummary
import hu.bme.mit.semantifyr.benchmark.analysis.EnginesReport
import hu.bme.mit.semantifyr.benchmark.analysis.Reference
import hu.bme.mit.semantifyr.benchmark.analysis.ResultsTable
import hu.bme.mit.semantifyr.benchmark.analysis.summarize
import hu.bme.mit.semantifyr.benchmark.campaigns.BenchmarkSources
import hu.bme.mit.semantifyr.benchmark.campaigns.Campaigns
import hu.bme.mit.semantifyr.benchmark.verification.VerificationCellAction
import hu.bme.mit.semantifyr.logging.error
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText
import kotlin.system.exitProcess

class BenchmarkOptions(
    val command: String,
    val arguments: List<String>,
    private val values: Map<String, String>,
    private val flags: Set<String>,
) {
    fun path(name: String, default: String): Path {
        return Path.of(values[name] ?: default)
    }

    fun pathOrNull(name: String): Path? {
        return values[name]?.let {
            Path.of(it)
        }
    }

    fun value(name: String): String? {
        return values[name]
    }

    fun flag(name: String): Boolean {
        return name in flags
    }

    fun argument(index: Int, name: String): String {
        require(index < arguments.size) { "$command needs the $name" }
        return arguments[index]
    }

    companion object {
        private val FLAGS = setOf("fresh", "no-runexec")

        fun parse(arguments: List<String>): BenchmarkOptions {
            require(arguments.isNotEmpty()) { USAGE }
            val positional = mutableListOf<String>()
            val values = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var index = 1
            while (index < arguments.size) {
                val argument = arguments[index]
                val name = argument.removePrefix("--")
                if (!argument.startsWith("--")) {
                    positional += argument
                    index += 1
                } else if (name in FLAGS) {
                    flags += name
                    index += 1
                } else {
                    require(index + 1 < arguments.size) { "--$name needs a value" }
                    values[name] = arguments[index + 1]
                    index += 2
                }
            }
            return BenchmarkOptions(arguments[0], positional, values, flags)
        }

        const val USAGE = "usage: semantifyr-benchmark run <campaign> | reference <campaign> | analyze <results directory> <output directory>"
    }
}

object Benchmark {
    private val logger by loggerFactory()

    private val json = Json {
        prettyPrint = true
    }

    suspend fun execute(arguments: List<String>): Int {
        try {
            val options = BenchmarkOptions.parse(arguments)
            return when (options.command) {
                "run" -> run(options)
                "reference" -> reference(options)
                "analyze" -> analyze(options)
                else -> throw IllegalArgumentException(BenchmarkOptions.USAGE)
            }
        } catch (exception: IllegalArgumentException) {
            logger.error { exception.message.orEmpty() }
            return 2
        }
    }

    private suspend fun run(options: BenchmarkOptions): Int {
        val definition = Campaigns.byName(options.argument(0, "campaign name"))
        val sysmlExamples = options.path("sysml-examples", "models/sysml")
        val oxstsModels = options.path("oxsts-models", "models")
        require(sysmlExamples.isDirectory()) { "No SysML examples directory at $sysmlExamples" }
        require(oxstsModels.isDirectory()) { "No OXSTS models directory at $oxstsModels" }
        val store = ResultStore(outputDirectory(options).resolve(definition.name))
        val referenceFile = referenceFile(options, definition.name)
        val baseline = options.pathOrNull("baseline")
        val tolerance = options.value("time-tolerance")?.toDouble() ?: DEFAULT_TIME_TOLERANCE
        require(baseline == null || ResultStore(baseline).resultsFile.exists()) { "No results.jsonl under the baseline $baseline" }

        val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        val campaign = definition.create(BenchmarkSources(injector, sysmlExamples, oxstsModels))
        if (options.flag("fresh")) {
            store.clear()
        }
        val runexecOptions = runexecOptions(options)
        store.writeEnvironment(RunEnvironment.of(campaign, runexecOptions != null))
        val action = VerificationCellAction(injector, campaign.timeout, ExecutionEnvironment.builder().build(), store.directory.resolve("models"), runexecOptions)
        val records = CampaignRunner(campaign, store, action).run()
        val summaries = summarizeRun(campaign.name, records, store)

        val differences = if (referenceFile.exists()) {
            Reference.compare(Reference.read(referenceFile), Reference.of(summaries))
        } else {
            emptyList()
        }
        val regressions = if (baseline != null) {
            Reference.compareTimes(summarize(ResultStore(baseline).load()), summaries, tolerance)
        } else {
            emptyList()
        }
        (differences + regressions).forEach {
            logger.error { it }
        }
        if (referenceFile.exists()) {
            logger.info { "Reference $referenceFile: ${differences.size} differences" }
        }
        return if (differences.isEmpty() && regressions.isEmpty()) {
            0
        } else {
            1
        }
    }

    private fun reference(options: BenchmarkOptions): Int {
        val campaign = options.argument(0, "campaign name")
        val store = ResultStore(outputDirectory(options).resolve(campaign))
        val records = store.load()
        require(records.isNotEmpty()) { "No results at ${store.resultsFile}, run the campaign first" }
        val referenceFile = referenceFile(options, campaign)
        Reference.write(referenceFile, Reference.of(summarize(records)))
        val analysisDirectory = referenceFile.resolveSibling(campaign)
        CampaignAnalysis(records, store.loadEnvironment()).write(analysisDirectory)
        logger.info { "Wrote $referenceFile and the analysis in $analysisDirectory" }
        return 0
    }

    private fun analyze(options: BenchmarkOptions): Int {
        val store = ResultStore(Path.of(options.argument(0, "results directory")))
        val output = Path.of(options.argument(1, "output directory"))
        val records = store.load()
        require(records.isNotEmpty()) { "No results at ${store.resultsFile}" }
        CampaignAnalysis(records, store.loadEnvironment()).write(output)
        logger.info { "Wrote the analysis of ${store.directory} to $output" }
        return 0
    }

    private fun runexecOptions(options: BenchmarkOptions): List<String>? {
        if (options.flag("no-runexec")) {
            return null
        }
        val runexecOptions = (options.value("runexec-options") ?: DEFAULT_RUNEXEC_OPTIONS).split(' ').filter {
            it.isNotBlank()
        }
        if (!Runexec(runexecOptions).isUsable()) {
            logger.info { "runexec is not installed or cannot run commands here, the backends run without it" }
            return null
        }
        logger.info { "The backends run under runexec ${runexecOptions.joinToString(" ")}" }
        return runexecOptions
    }

    private fun summarizeRun(
        campaign: String,
        records: List<BenchmarkRecord>,
        store: ResultStore,
    ): List<CellSummary> {
        val summaries = summarize(records)
        val markdown = ResultsTable.markdown(campaign, summaries)
        store.directory.resolve("results.csv").writeText(ResultsTable.csv(summaries))
        store.directory.resolve("summary.md").writeText(markdown)
        store.directory.resolve("engines.json").writeText(json.encodeToString(EnginesReport.derive(summaries)))
        CampaignAnalysis(records, store.loadEnvironment()).write(store.directory.resolve("analysis"))
        logger.info { "Results in ${store.directory}\n$markdown" }
        return summaries
    }

    private fun outputDirectory(options: BenchmarkOptions): Path {
        return options.path("output", "results")
    }

    private fun referenceFile(options: BenchmarkOptions, campaign: String): Path {
        return options.path("reference", "reference").resolve("$campaign.jsonl")
    }

    private const val DEFAULT_TIME_TOLERANCE = 0.2

    // container mode would discard what the backends write into their working directories
    private const val DEFAULT_RUNEXEC_OPTIONS = "--no-container"
}

suspend fun main(arguments: Array<String>) {
    exitProcess(Benchmark.execute(arguments.toList()))
}
