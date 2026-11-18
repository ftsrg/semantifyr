/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.benchmark.Benchmark
import hu.bme.mit.semantifyr.benchmark.BenchmarkRun
import hu.bme.mit.semantifyr.benchmark.FreshJvmRun
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.combinations
import hu.bme.mit.semantifyr.benchmark.freshJvm
import hu.bme.mit.semantifyr.benchmark.interleavedRounds
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.serialization.Serializable

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

private val models = mapOf(
    "crossroads" to ModelCase("build/benchmark-models/library", "build/benchmark-models/crossroads.semantifyr", "TrafficLightABehaviorNormalGreenUnreachableViolated"),
    "spacecraft" to ModelCase("build/benchmark-models/library", "build/benchmark-models/compressedspacecraft.semantifyr", "SpacecraftBehaviorCommunicationTransmittingSendDataSendingReachable"),
)

private val optimizations = mapOf(
    "all" to OptimizationConfig.ALL,
    "without-copy-propagation" to OptimizationConfig(OptimizationPass.entries.toSet() - OptimizationPass.CopyPropagation),
)

private val phases = listOf(
    "create",
    "instantiation",
    "inlining",
    "flattening",
)

class CompilationPhasesRun(private val case: CompilerCase) : FreshJvmRun<PhasesResult> {
    override fun run(measurement: Measurement): PhasesResult {
        val compilation = CaseCompilation(case.model, case.optimization)
        repeat(10) {
            compilation.phases(measurement.warmUp().spans)
        }
        val compiled = compilation.phases(measurement.measured().spans)
        return PhasesResult(
            inliningOptimization = compiled.flattened.compilationMetrics.inliningOptimization,
            flatteningOptimization = compiled.flattened.compilationMetrics.flatteningOptimization,
            inlinedSize = compiled.inlinedSize,
            flattenedSize = compiled.flattenedSize,
        )
    }
}

class CompilationPhasesBenchmark : Benchmark<PhasesResult>() {
    private val logger by loggerFactory()

    private val cases = combinations(models, optimizations, ::CompilerCase)

    override fun measure(): BenchmarkRun<PhasesResult> {
        return interleavedRounds(cases) { case ->
            freshJvm(CompilationPhasesRun::class, case)
        }
    }

    override fun analyze(run: BenchmarkRun<PhasesResult>) {
        for (configuration in run.statement.configurations.keys) {
            val repetitions = run.repetitionsOf(configuration)
            val peaks = repetitions.mapNotNull {
                it.jvm?.peakMemoryBytes?.shr(20)
            }
            logger.info { "$configuration: peak memory of the fresh JVM ${peaks.joinToString()} MiB" }
            repetitions.firstNotNullOfOrNull {
                it.measuredCall?.result
            }?.flatteningOptimization?.passes?.forEach {
                logger.info { "  flattening pass ${it.name}: ${it.runs} runs, ${it.firings} firings, ${it.duration} as the compiler counts it" }
            }
        }
        for (model in models.keys) {
            for (phase in phases) {
                val baseline = phaseSeconds(run, "$model/all", phase)
                val compared = phaseSeconds(run, "$model/without-copy-propagation", phase)
                if (baseline.size < 2 || compared.size < 2) {
                    logger.info { "$model $phase: too few measured calls for a relation" }
                } else {
                    logger.info { "$model $phase, without copy propagation over all: ${ratioOfMeans(baseline, compared)}" }
                }
            }
        }
    }
}

private fun phaseSeconds(
    run: BenchmarkRun<PhasesResult>,
    configuration: String,
    phase: String,
): List<Double> {
    return run.repetitionsOf(configuration).mapNotNull {
        it.measuredCall?.spanSeconds(phase)
    }
}
