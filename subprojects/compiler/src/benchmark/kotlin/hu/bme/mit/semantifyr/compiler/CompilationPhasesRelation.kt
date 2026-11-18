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
import java.util.Locale
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
    for (configuration in run.statement.configurations.keys) {
        val repetitions = byConfiguration[configuration].orEmpty()
        val measured = repetitions.mapNotNull {
            measuredCall(it)
        }
        println("$configuration: ${measured.size} measured calls in ${repetitions.size} repetitions")
        if (measured.isNotEmpty()) {
            for (phase in phases) {
                println("  $phase %.1f ms".format(Locale.ROOT, phaseMilliseconds(repetitions, phase).average()))
            }
        }
        val peaks = repetitions.flatMap {
            it.processes
        }.map {
            it.peakMemoryBytes / (1 shl 20)
        }
        println("  peak memory of the fresh JVM: ${peaks.joinToString()} MiB")
        measured.firstOrNull()?.result?.flatteningOptimization?.passes?.forEach {
            println("  flattening pass ${it.name}: ${it.runs} runs, ${it.firings} firings, %.1f ms as the compiler counts it".format(Locale.ROOT, it.duration.inWholeMicroseconds / 1e3))
        }
    }
    for (model in listOf("crossroads", "spacecraft")) {
        val all = byConfiguration["$model-all"].orEmpty()
        val withoutCopyPropagation = byConfiguration["$model-without-copy-propagation"].orEmpty()
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
    if (loads.isNotEmpty()) {
        println("load average before the repetitions: %.1f to %.1f on ${run.context.processors} processors".format(Locale.ROOT, loads.min(), loads.max()))
    }
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
