/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.BenchmarkRun
import hu.bme.mit.semantifyr.benchmark.BenchmarkSource
import hu.bme.mit.semantifyr.benchmark.Call
import hu.bme.mit.semantifyr.benchmark.FreshJvm
import hu.bme.mit.semantifyr.benchmark.Trial
import hu.bme.mit.semantifyr.benchmark.benchmark
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.compiler.CaseCompilation
import hu.bme.mit.semantifyr.compiler.ModelCase
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.Serializable
import org.slf4j.Logger
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

class CompilationAndThetaBenchmark : BenchmarkSource {
    override val benchmark = benchmark(
        configurations = mapOf(
            "crossroads/all" to OptimizationCell(crossroads, OptimizationPass.entries.toSet()),
            "crossroads/none" to OptimizationCell(crossroads, emptySet()),
        ),
        isolation = FreshJvm(warmUpCalls = 3),
        timeLimit = 300.seconds,
        context = listOf(
            ThetaInstallation,
        ),
        analysis = ::relate,
    ) { configuration ->
        val compilation = CaseCompilation(configuration.model, OptimizationConfig(configuration.passes))
        val theta = MeasuredTheta()
        Trial {
            val compiled = compilation.phases(it.spans)
            CompiledThetaResult(compiled.flattenedSize, theta.verify(compiled.flattened, it.processes))
        }
    }
}

private fun relate(run: BenchmarkRun<OptimizationCell, CompiledThetaResult>, logger: Logger) {
    val baseline = run.repetitionsOf("crossroads/all").mapNotNull {
        it.measuredCall
    }
    val compared = run.repetitionsOf("crossroads/none").mapNotNull {
        it.measuredCall
    }
    logger.info { "none over all, compilation ${ratioOfMeans(compilationSeconds(baseline), compilationSeconds(compared))}" }
    logger.info { "none over all, Theta ${ratioOfMeans(thetaSeconds(baseline), thetaSeconds(compared))}" }
    val baselineTotal = compilationSeconds(baseline).zip(thetaSeconds(baseline), Double::plus)
    val comparedTotal = compilationSeconds(compared).zip(thetaSeconds(compared), Double::plus)
    logger.info { "none over all, compilation and Theta together ${ratioOfMeans(baselineTotal, comparedTotal)}" }
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
