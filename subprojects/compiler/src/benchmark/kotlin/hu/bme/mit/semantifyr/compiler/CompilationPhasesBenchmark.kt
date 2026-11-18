/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.benchmark.BenchmarkRun
import hu.bme.mit.semantifyr.benchmark.BenchmarkSource
import hu.bme.mit.semantifyr.benchmark.FreshJvm
import hu.bme.mit.semantifyr.benchmark.Trial
import hu.bme.mit.semantifyr.benchmark.benchmark
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.Serializable
import org.slf4j.Logger

@Serializable
data class CompilerCase(
    val model: ModelCase,
    val optimization: OptimizationConfig,
)

@Serializable
data class PhasesResult(
    val inliningOptimization: OptimizationStats,
    val flatteningOptimization: OptimizationStats,
    val inlinedSize: ModelSize,
    val flattenedSize: ModelSize,
)

private val crossroads = ModelCase("build/benchmark-models/library", "build/benchmark-models/crossroads.semantifyr", "TrafficLightABehaviorNormalGreenUnreachableViolated")

private val spacecraft = ModelCase("build/benchmark-models/library", "build/benchmark-models/compressedspacecraft.semantifyr", "SpacecraftBehaviorCommunicationTransmittingSendDataSendingReachable")

private val withoutCopyPropagation = OptimizationConfig(OptimizationPass.entries.toSet() - OptimizationPass.CopyPropagation)

private val phases = listOf(
    "create",
    "instantiation",
    "inlining",
    "flattening",
)

class CompilationPhasesBenchmark : BenchmarkSource {
    override val benchmark = benchmark(
        configurations = mapOf(
            "crossroads-all" to CompilerCase(crossroads, OptimizationConfig.ALL),
            "crossroads-without-copy-propagation" to CompilerCase(crossroads, withoutCopyPropagation),
            "spacecraft-all" to CompilerCase(spacecraft, OptimizationConfig.ALL),
            "spacecraft-without-copy-propagation" to CompilerCase(spacecraft, withoutCopyPropagation),
        ),
        isolation = FreshJvm(warmUpCalls = 10),
        analysis = ::relate,
    ) { configuration ->
        val compilation = CaseCompilation(configuration.model, configuration.optimization)
        Trial {
            val compiled = compilation.phases(it.spans)
            PhasesResult(
                inliningOptimization = compiled.flattened.compilationMetrics.inliningOptimization,
                flatteningOptimization = compiled.flattened.compilationMetrics.flatteningOptimization,
                inlinedSize = compiled.inlinedSize,
                flattenedSize = compiled.flattenedSize,
            )
        }
    }
}

private fun relate(run: BenchmarkRun<CompilerCase, PhasesResult>, logger: Logger) {
    for (configuration in run.statement.configurations.keys) {
        val repetitions = run.repetitionsOf(configuration)
        val peaks = repetitions.mapNotNull {
            it.jvm?.peakMemoryBytes?.shr(20)
        }
        logger.info { "$configuration: peak memory of the fresh JVM ${peaks.joinToString()} MiB" }
        repetitions.firstNotNullOfOrNull {
            it.measuredCall
        }?.result?.flatteningOptimization?.passes?.forEach {
            logger.info { "  flattening pass ${it.name}: ${it.runs} runs, ${it.firings} firings, ${it.duration} as the compiler counts it" }
        }
    }
    for (model in listOf("crossroads", "spacecraft")) {
        for (phase in phases) {
            val baseline = phaseSeconds(run, "$model-all", phase)
            val compared = phaseSeconds(run, "$model-without-copy-propagation", phase)
            if (baseline.size < 2 || compared.size < 2) {
                logger.info { "$model $phase: too few measured calls for a relation" }
            } else {
                logger.info { "$model $phase, without copy propagation over all: ${ratioOfMeans(baseline, compared)}" }
            }
        }
    }
}

private fun phaseSeconds(run: BenchmarkRun<CompilerCase, PhasesResult>, configuration: String, phase: String): List<Double> {
    return run.repetitionsOf(configuration).mapNotNull {
        it.measuredCall?.spanSeconds(phase)
    }
}
