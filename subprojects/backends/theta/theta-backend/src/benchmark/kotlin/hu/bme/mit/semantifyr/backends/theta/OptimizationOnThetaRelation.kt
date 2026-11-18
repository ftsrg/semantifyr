/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.Repetition
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.benchmark.readBenchmarkRun
import java.util.Locale
import kotlin.io.path.Path

fun main(arguments: Array<String>) {
    val run = readBenchmarkRun<OptimizationCell, ThetaResult>(Path(arguments.single()))
    val byConfiguration = run.repetitions.groupBy {
        it.configuration
    }
    // a set counts only when Theta decided it in every repetition within the limit, so no cut run shortens a mean
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
            println("$configuration: ${outcomes(byConfiguration[configuration].orEmpty())}")
        } else if (baseline == null || compared == null) {
            println("$configuration: not comparable with all, ${outcomes(byConfiguration[configuration].orEmpty())}")
        } else {
            println("$configuration over all, wall time ${ratioOfMeans(wallTimes(baseline), wallTimes(compared))}, CPU time ${ratioOfMeans(cpuTimes(baseline), cpuTimes(compared))}")
        }
    }
    val loads = run.repetitions.map {
        it.hostState.loadBefore.oneMinute
    }
    if (loads.isNotEmpty()) {
        println("load average before the repetitions: %.1f to %.1f on ${run.context.processors} processors".format(Locale.ROOT, loads.min(), loads.max()))
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
