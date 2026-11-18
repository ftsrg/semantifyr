/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.benchmark.BenchmarkRun
import hu.bme.mit.semantifyr.benchmark.BenchmarkSource
import hu.bme.mit.semantifyr.benchmark.ProcessObservation
import hu.bme.mit.semantifyr.benchmark.Repetition
import hu.bme.mit.semantifyr.benchmark.RunnerJvm
import hu.bme.mit.semantifyr.benchmark.Trial
import hu.bme.mit.semantifyr.benchmark.benchmark
import hu.bme.mit.semantifyr.benchmark.ratioOfMeans
import hu.bme.mit.semantifyr.compiler.CaseCompilation
import hu.bme.mit.semantifyr.compiler.ModelCase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.logging.info
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OptimizationCell(
    val model: ModelCase,
    val passes: Set<OptimizationPass>,
)

private val models = mapOf(
    "sysml-crossroads" to ModelCase("build/benchmark-models/sysml/library", "build/benchmark-models/sysml/crossroads.semantifyr", "TrafficLightABehaviorNormalGreenUnreachableViolated"),
    "gamma-level-crossing" to ModelCase("build/benchmark-models/gamma/library", "build/benchmark-models/gamma/LevelCrossing.semantifyr", "_TwoTrainsCanBeInTheCrossing"),
    "tutorial-hierarchy" to ModelCase(null, "build/benchmark-models/tutorial/hierarchy.semantifyr", "NeverBothGreen"),
)

private val allPasses = OptimizationPass.entries.toSet()

private val optimizationSets = mapOf(
    "all" to allPasses,
    "none" to emptySet(),
) + OptimizationPass.entries.associate {
    "without-$it" to allPasses - it
}

class OptimizationOnThetaBenchmark : BenchmarkSource {
    override val benchmark = benchmark(
        configurations = models.flatMap { (modelName, model) ->
            optimizationSets.map { (setName, passes) ->
                "$modelName/$setName" to OptimizationCell(model, passes)
            }
        }.toMap(),
        isolation = RunnerJvm,
        timeLimit = 60.seconds,
        context = listOf(
            ThetaInstallation,
        ),
        analysis = ::relate,
    ) { configuration ->
        val compiled = CaseCompilation(configuration.model, OptimizationConfig(configuration.passes)).whole()
        val theta = MeasuredTheta()
        Trial {
            theta.verify(compiled, it.processes)
        }
    }
}

private fun relate(run: BenchmarkRun<OptimizationCell, ThetaResult>, logger: Logger) {
    for (configuration in run.statement.configurations.keys) {
        val baselineName = "${configuration.substringBefore('/')}/all"
        val baseline = run.repetitionsOf(baselineName)
        val compared = run.repetitionsOf(configuration)
        // a cut or undecided repetition would shorten a mean
        if (configuration == baselineName || !decidedByTheta(baseline) || !decidedByTheta(compared)) {
            logger.info { "$configuration: ${outcomes(compared)}" }
        } else {
            val wallTime = ratioOfMeans(thetaSeconds(baseline, ProcessObservation::wallTimeSeconds), thetaSeconds(compared, ProcessObservation::wallTimeSeconds))
            val cpuTime = ratioOfMeans(thetaSeconds(baseline, ProcessObservation::cpuTimeSeconds), thetaSeconds(compared, ProcessObservation::cpuTimeSeconds))
            logger.info { "$configuration over all, wall time $wallTime, CPU time $cpuTime" }
        }
    }
}

private fun decidedByTheta(repetitions: List<Repetition<ThetaResult>>): Boolean {
    return repetitions.size >= 2 && repetitions.all {
        val call = it.measuredCall
        val process = call?.processes?.singleOrNull()
        call != null && call.result.verdict.isDecisive && process != null && process.terminationReason == null
    }
}

private fun thetaSeconds(repetitions: List<Repetition<ThetaResult>>, seconds: (ProcessObservation) -> Double): List<Double> {
    return repetitions.map {
        seconds(checkNotNull(it.measuredCall).processes.single())
    }
}

private fun outcomes(repetitions: List<Repetition<ThetaResult>>): String {
    return repetitions.groupingBy {
        val call = it.measuredCall
        val cut = call?.processes?.firstNotNullOfOrNull { process ->
            process.terminationReason
        }
        val failure = it.failure
        if (failure != null) {
            "${failure.stage} failed: ${failure.stackTrace.lines().first()}"
        } else if (cut != null) {
            "${call.result.verdict} cut at $cut"
        } else {
            "${call?.result?.verdict} with ${call?.processes?.size} processes"
        }
    }.eachCount().toString()
}
