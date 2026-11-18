/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.Repetition
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.benchmark.readBenchmarkRun
import hu.bme.mit.semantifyr.logging.info
import org.slf4j.LoggerFactory
import java.util.Locale
import kotlin.io.path.Path

private val logger = LoggerFactory.getLogger("OptimizationOnThetaRelation")

fun main(arguments: Array<String>) {
    val run = readBenchmarkRun<OptimizationCell, ThetaResult>(Path(arguments.single()))
    val byConfiguration = run.repetitions.groupBy {
        it.configuration
    }
    // a cut or undecided repetition would shorten a mean
    val decided = byConfiguration.filterValues { repetitions ->
        repetitions.size >= 2 && repetitions.all {
            isDecidedByTheta(it)
        }
    }
    for (configuration in run.statement.configurations.keys) {
        val modelName = configuration.substringBefore('/')
        val baseline = decided["$modelName/all"]
        val compared = decided[configuration]
        if (configuration == "$modelName/all") {
            logger.info { "$configuration: ${outcomes(byConfiguration[configuration].orEmpty())}" }
        } else if (baseline == null || compared == null) {
            logger.info { "$configuration: not comparable with all, ${outcomes(byConfiguration[configuration].orEmpty())}" }
        } else {
            val wallTime = ratioOfMeans(wallTimes(baseline), wallTimes(compared))
            val cpuTime = ratioOfMeans(cpuTimes(baseline), cpuTimes(compared))
            logger.info { "$configuration over all, wall time $wallTime, CPU time $cpuTime" }
        }
    }
    val loads = run.repetitions.map {
        it.hostState.loadBefore.oneMinute
    }
    if (loads.isNotEmpty()) {
        val range = "%.1f to %.1f".format(Locale.ROOT, loads.min(), loads.max())
        logger.info { "load average before the repetitions: $range on ${run.context.processors} processors" }
    }
}

private fun isDecidedByTheta(repetition: Repetition<ThetaResult>): Boolean {
    val process = repetition.processes.singleOrNull()
    val result = repetition.calls.singleOrNull()?.result
    return process != null && process.terminationReason == null && result != null && result.verdict.isDecisive
}

private fun outcomes(repetitions: List<Repetition<ThetaResult>>): String {
    return repetitions.groupingBy {
        val result = it.calls.singleOrNull()?.result
        val failure = it.failure
        val cut = it.processes.firstNotNullOfOrNull { process ->
            process.terminationReason
        }
        if (failure != null) {
            "${failure.stage} failed: ${failure.stackTrace.lines().first()}"
        } else if (cut != null) {
            "${result?.verdict} cut at $cut"
        } else {
            "${result?.verdict} with ${it.processes.size} processes"
        }
    }.eachCount().toString()
}

private fun wallTimes(repetitions: List<Repetition<ThetaResult>>): List<Double> {
    return repetitions.map {
        it.processes.single().wallTimeSeconds
    }
}

private fun cpuTimes(repetitions: List<Repetition<ThetaResult>>): List<Double> {
    return repetitions.map {
        it.processes.single().cpuTimeSeconds
    }
}
