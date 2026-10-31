/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
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
    val campaign: String,
    private val values: Map<String, String>,
    private val flags: Set<String>,
) {
    fun path(name: String): Path {
        return pathOrNull(name) ?: throw IllegalArgumentException("--$name is required")
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

    companion object {
        private val FLAGS = setOf("fresh")

        fun parse(arguments: List<String>): BenchmarkOptions {
            require(arguments.size >= 2) { "usage: benchmark <run|reference> <campaign> [--option value]..." }
            val values = mutableMapOf<String, String>()
            val flags = mutableSetOf<String>()
            var index = 2
            while (index < arguments.size) {
                val name = arguments[index].removePrefix("--")
                require(arguments[index].startsWith("--")) { "Unexpected argument ${arguments[index]}" }
                if (name in FLAGS) {
                    flags += name
                    index += 1
                } else {
                    require(index + 1 < arguments.size) { "--$name needs a value" }
                    values[name] = arguments[index + 1]
                    index += 2
                }
            }
            return BenchmarkOptions(arguments[0], arguments[1], values, flags)
        }
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
                else -> throw IllegalArgumentException("Unknown command ${options.command}, expected run or reference")
            }
        } catch (exception: IllegalArgumentException) {
            logger.error { exception.message.orEmpty() }
            return 2
        }
    }

    private suspend fun run(options: BenchmarkOptions): Int {
        val definition = Campaigns.byName(options.campaign)
        val sysmlExamples = options.path("sysml-examples")
        val oxstsModels = options.path("oxsts-models")
        require(sysmlExamples.isDirectory()) { "No SysML examples directory at $sysmlExamples" }
        require(oxstsModels.isDirectory()) { "No OXSTS models directory at $oxstsModels" }
        val store = ResultStore(options.path("output").resolve(definition.name))
        val referenceFile = referenceFile(options, definition.name)
        val baseline = options.pathOrNull("baseline")
        val tolerance = options.value("time-tolerance")?.toDouble() ?: DEFAULT_TIME_TOLERANCE
        require(baseline == null || ResultStore(baseline).resultsFile.exists()) { "No results.jsonl under the baseline $baseline" }

        val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        val campaign = definition.create(BenchmarkSources(injector, sysmlExamples, oxstsModels))
        if (options.flag("fresh")) {
            store.clear()
        }
        val action = VerificationCellAction(injector, campaign.timeout, ExecutionEnvironment.builder().build(), store.directory.resolve("models"))
        val records = CampaignRunner(campaign, store, action).run()
        val summaries = analyze(campaign.name, records, store)

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
        val store = ResultStore(options.path("output").resolve(options.campaign))
        val records = store.load()
        require(records.isNotEmpty()) { "No results at ${store.resultsFile}, run the campaign first" }
        val referenceFile = referenceFile(options, options.campaign)
        Reference.write(referenceFile, Reference.of(summarize(records)))
        logger.info { "Wrote $referenceFile" }
        return 0
    }

    private fun analyze(
        campaign: String,
        records: List<BenchmarkRecord>,
        store: ResultStore,
    ): List<CellSummary> {
        val summaries = summarize(records)
        val markdown = ResultsTable.markdown(campaign, summaries)
        store.directory.resolve("results.csv").writeText(ResultsTable.csv(summaries))
        store.directory.resolve("summary.md").writeText(markdown)
        store.directory.resolve("engines.json").writeText(json.encodeToString(EnginesReport.derive(summaries)))
        logger.info { "Results in ${store.directory}\n$markdown" }
        return summaries
    }

    private fun referenceFile(options: BenchmarkOptions, campaign: String): Path {
        return options.path("reference").resolve("$campaign.jsonl")
    }

    private const val DEFAULT_TIME_TOLERANCE = 0.2

}

suspend fun main(arguments: Array<String>) {
    exitProcess(Benchmark.execute(arguments.toList()))
}
