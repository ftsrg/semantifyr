/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.benchmark.Call
import hu.bme.mit.semantifyr.benchmark.Repetition
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.benchmark.readBenchmarkRun
import kotlin.io.path.Path

private val phases = listOf(
    "create",
    "instantiation",
    "inlining",
    "flattening",
)

fun main(arguments: Array<String>) {
    val run = readBenchmarkRun<CompilerCase, PhasesResult>(Path(arguments.single()))
    val byConfiguration = run.repetitions.groupBy {
        it.configuration
    }
    for ((configuration, repetitions) in byConfiguration) {
        val failed = repetitions.filter {
            it.failure != null || measuredCall(it) == null
        }
        println("$configuration: ${repetitions.size - failed.size} measured calls, ${failed.size} repetitions without one")
        for (phase in phases) {
            println("  $phase %.1f ms".format(phaseMilliseconds(repetitions, phase).average()))
        }
        val peaks = repetitions.flatMap {
            it.processes
        }.map {
            it.peakMemoryBytes / (1 shl 20)
        }
        println("  peak memory of the fresh JVM: ${peaks.joinToString()} MiB")
        measuredCall(repetitions.first())?.result?.flatteningOptimization?.passes?.forEach {
            println("  flattening pass ${it.name}: ${it.runs} runs, ${it.firings} firings, %.1f ms as the compiler counts it".format(it.duration.inWholeMicroseconds / 1e3))
        }
    }
    for (model in listOf("crossroads", "spacecraft")) {
        val all = byConfiguration.getValue("$model-all")
        val withoutCopyPropagation = byConfiguration.getValue("$model-without-copy-propagation")
        for (phase in phases) {
            val baseline = phaseMilliseconds(all, phase)
            val compared = phaseMilliseconds(withoutCopyPropagation, phase)
            if (baseline.size < 2 || compared.size < 2) {
                println("$model $phase: too few measured calls for a relation")
            } else {
                println("$model $phase, without copy propagation over all: ${ratioOfMeans(baseline, compared)}")
            }
        }
    }
    val loads = run.repetitions.map {
        it.hostState.loadBefore.oneMinute
    }
    println("load average before the repetitions: %.1f to %.1f on ${run.context.processors} processors".format(loads.min(), loads.max()))
}

private fun measuredCall(repetition: Repetition<PhasesResult>): Call<PhasesResult>? {
    return repetition.calls.singleOrNull {
        !it.warmUp
    }
}

private fun phaseMilliseconds(repetitions: List<Repetition<PhasesResult>>, phase: String): List<Double> {
    return repetitions.mapNotNull {
        measuredCall(it)
    }.map { call ->
        call.spans.single {
            it.name == phase
        }.wallTimeNanoseconds / 1e6
    }
}
