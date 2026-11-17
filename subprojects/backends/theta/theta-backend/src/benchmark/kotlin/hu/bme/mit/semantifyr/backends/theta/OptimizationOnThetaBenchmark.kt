/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OptimizationCell(
    val model: CaseModel,
    val passes: Set<OptimizationPass>,
)

private val models = mapOf(
    "sysml-crossroads" to CaseModel("build/benchmark-models/sysml/library", "build/benchmark-models/sysml/crossroads.semantifyr", "TrafficLightABehaviorNormalGreenUnreachableViolated"),
    "gamma-level-crossing" to CaseModel("build/benchmark-models/gamma/library", "build/benchmark-models/gamma/LevelCrossing.semantifyr", "_TwoTrainsCanBeInTheCrossing"),
    "tutorial-hierarchy" to CaseModel(null, "build/benchmark-models/tutorial/hierarchy.semantifyr", "NeverBothGreen"),
)

private val allPasses = OptimizationPass.entries.toSet()

private val optimizationSets = mapOf(
    "all" to allPasses,
    "none" to emptySet(),
) + OptimizationPass.entries.associate {
    "without-$it" to allPasses - it
}

class OptimizationOnThetaBenchmark : ThetaBenchmark<OptimizationCell>() {

    override val timeLimit = 60.seconds

    override val configurations = models.flatMap { (modelName, model) ->
        optimizationSets.map { (setName, passes) ->
            "$modelName/$setName" to OptimizationCell(model, passes)
        }
    }.toMap()

    override fun compile(configuration: OptimizationCell): FlattenedCompilationContext {
        return compile(configuration.model, OptimizationConfig(configuration.passes))
    }
}
