/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.benchmark.Spans
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import kotlinx.serialization.Serializable

@Serializable
data class PhasesResult(
    val inliningOptimization: OptimizationStats,
    val flatteningOptimization: OptimizationStats,
    val inlinedSize: ModelSize,
    val flattenedSize: ModelSize,
)

private const val CROSSROADS_CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"
private const val SPACECRAFT_CASE = "SpacecraftBehaviorCommunicationTransmittingSendDataSendingReachable"

private val withoutCopyPropagation = OptimizationConfig(OptimizationPass.entries.toSet() - OptimizationPass.CopyPropagation)

class CompilationPhasesBenchmark : CompilerBenchmark<PhasesResult>() {

    override val configurations = mapOf(
        "crossroads-all" to CompilerCase("crossroads", CROSSROADS_CASE, OptimizationConfig.ALL),
        "crossroads-without-copy-propagation" to CompilerCase("crossroads", CROSSROADS_CASE, withoutCopyPropagation),
        "spacecraft-all" to CompilerCase("compressedspacecraft", SPACECRAFT_CASE, OptimizationConfig.ALL),
        "spacecraft-without-copy-propagation" to CompilerCase("compressedspacecraft", SPACECRAFT_CASE, withoutCopyPropagation),
    )

    override val warmUpCalls = 10

    override fun call(spans: Spans): PhasesResult {
        val created = spans.measure("create") {
            creator.create(caseClass, outputDirectory)
        }
        return inCompilationScope(created) {
            val instantiated = spans.measure("instantiation") {
                instantiator.instantiate(created)
            }
            val inlined = spans.measure("inlining") {
                inliner.inline(instantiated)
            }
            val inlinedSize = sizeCounter.count(inlined)
            val flattened = spans.measure("flattening") {
                flattener.flatten(inlined)
            }
            PhasesResult(
                inliningOptimization = flattened.compilationMetrics.inliningOptimization,
                flatteningOptimization = flattened.compilationMetrics.flatteningOptimization,
                inlinedSize = inlinedSize,
                flattenedSize = sizeCounter.count(flattened.inlinedOxsts),
            )
        }
    }
}
