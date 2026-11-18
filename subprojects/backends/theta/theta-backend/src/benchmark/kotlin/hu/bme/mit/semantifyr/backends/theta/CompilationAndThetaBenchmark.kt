/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.Benchmark
import hu.bme.mit.semantifyr.benchmark.BenchmarkRun
import hu.bme.mit.semantifyr.benchmark.Call
import hu.bme.mit.semantifyr.benchmark.FreshJvmRun
import hu.bme.mit.semantifyr.benchmark.Measurement
import hu.bme.mit.semantifyr.benchmark.freshJvm
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.compiler.CaseCompilation
import hu.bme.mit.semantifyr.compiler.ModelCase
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class CompiledThetaResult(
    val flattenedSize: ModelSize,
    val theta: ThetaResult,
)

private val crossroads = ModelCase("build/benchmark-models/sysml/library", "build/benchmark-models/sysml/crossroads.semantifyr", "TrafficLightABehaviorNormalGreenUnreachableViolated")

private val phases = listOf(
    "create",
    "instantiation",
    "inlining",
    "flattening",
)

class CompilationAndThetaRun(private val cell: OptimizationCell) : FreshJvmRun<CompiledThetaResult> {
    override fun run(measurement: Measurement): CompiledThetaResult {
        val compilation = CaseCompilation(cell.model, OptimizationConfig(cell.passes))
        val theta = MeasuredTheta()
        repeat(3) {
            val warmUp = measurement.warmUp()
            theta.verify(compilation.phases(warmUp.spans).flattened, warmUp.processes)
        }
        val measured = measurement.measured()
        val compiled = compilation.phases(measured.spans)
        return CompiledThetaResult(compiled.flattenedSize, theta.verify(compiled.flattened, measured.processes))
    }
}

class CompilationAndThetaBenchmark : Benchmark<CompiledThetaResult>() {
    private val logger by loggerFactory()

    private val cells = mapOf(
        "crossroads/all" to OptimizationCell(crossroads, OptimizationPass.entries.toSet()),
        "crossroads/none" to OptimizationCell(crossroads, emptySet()),
    )

    override fun measure(): BenchmarkRun<CompiledThetaResult> {
        val run = BenchmarkRun<CompiledThetaResult>()
        run.record(ThetaInstallation)
        for ((name, cell) in cells) {
            repeat(5) {
                run.add(name, cell, freshJvm(CompilationAndThetaRun::class, cell, timeLimit = 300.seconds))
            }
        }
        return run
    }

    override fun analyze(run: BenchmarkRun<CompiledThetaResult>) {
        val baseline = run.repetitionsOf("crossroads/all").mapNotNull {
            it.measuredCall
        }
        val compared = run.repetitionsOf("crossroads/none").mapNotNull {
            it.measuredCall
        }
        logger.warn { "none over all, compilation ${ratioOfMeans(compilationSeconds(baseline), compilationSeconds(compared))}" }
        logger.warn { "none over all, Theta ${ratioOfMeans(thetaSeconds(baseline), thetaSeconds(compared))}" }
        val baselineTotal = compilationSeconds(baseline).zip(thetaSeconds(baseline), Double::plus)
        val comparedTotal = compilationSeconds(compared).zip(thetaSeconds(compared), Double::plus)
        logger.warn { "none over all, compilation and Theta together ${ratioOfMeans(baselineTotal, comparedTotal)}" }
    }
}

private fun compilationSeconds(calls: List<Call<CompiledThetaResult>>): List<Double> {
    return calls.map { call ->
        phases.sumOf {
            call.spanSeconds(it)
        }
    }
}

private fun thetaSeconds(calls: List<Call<CompiledThetaResult>>): List<Double> {
    return calls.map { call ->
        call.processes.sumOf {
            it.wallTimeSeconds
        }
    }
}
