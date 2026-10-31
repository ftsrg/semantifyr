/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class ModelSize(
    val variables: Int = 0,
    val operations: Int = 0,
    val expressions: Int = 0,
)

@Serializable
data class CompilationMetrics(
    val createDuration: Duration = Duration.ZERO,
    val instantiationDuration: Duration = Duration.ZERO,
    val inliningDuration: Duration = Duration.ZERO,
    val flatteningDuration: Duration = Duration.ZERO,
    val inliningOptimization: OptimizationStats = OptimizationStats(),
    val flatteningOptimization: OptimizationStats = OptimizationStats(),
    val inputSize: ModelSize = ModelSize(),
    val inlinedSize: ModelSize = ModelSize(),
    val flattenedSize: ModelSize = ModelSize(),
) {
    val unfoldingDuration: Duration
        get() = createDuration + instantiationDuration + inliningDuration + flatteningDuration
}

interface CompilationContext {
    val inlinedOxsts: InlinedOxsts
}

interface EvaluableCompilationContext : CompilationContext {
    val rootInstance: Instance
}

class CreatedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
) : CompilationContext {

    fun instantiated(instanceTree: InstanceTree): InstantiatedCompilationContext {
        return InstantiatedCompilationContext(inlinedOxsts, instanceTree)
    }

}

class InstantiatedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    val instanceTree: InstanceTree,
) : EvaluableCompilationContext {

    override val rootInstance: Instance
        get() = instanceTree.rootInstance

    fun inlined(transitionCallTraces: TransitionCallTraceMap, optimization: OptimizationStats): InlinedCompilationContext {
        return InlinedCompilationContext(inlinedOxsts, instanceTree, transitionCallTraces, optimization)
    }

}

class InlinedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    val instanceTree: InstanceTree,
    val transitionCallTraces: TransitionCallTraceMap,
    val optimization: OptimizationStats,
) : EvaluableCompilationContext {

    override val rootInstance: Instance
        get() = instanceTree.rootInstance

    fun flattened(flatteningInfo: FlatteningInfo, flatteningOptimization: OptimizationStats): FlattenedCompilationContext {
        return FlattenedCompilationContext(
            inlinedOxsts = inlinedOxsts,
            rootInstance = instanceTree.rootInstance,
            transitionCallTraces = transitionCallTraces,
            flatteningInfo = flatteningInfo,
            compilationMetrics = CompilationMetrics(
                inliningOptimization = optimization,
                flatteningOptimization = flatteningOptimization,
            ),
        )
    }

}

class FlattenedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    override val rootInstance: Instance,
    val transitionCallTraces: TransitionCallTraceMap,
    val flatteningInfo: FlatteningInfo,
    val compilationMetrics: CompilationMetrics = CompilationMetrics(),
) : EvaluableCompilationContext

class ArraySlot(
    val arrayVariable: VariableDeclaration,
    val index: Int,
)

class FlatteningInfo(
    val variableHolders: Map<VariableDeclaration, Instance>,
    val variableInstanceDomains: Map<VariableDeclaration, Set<Instance>>,
    val variableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    val instanceIdMapping: InstanceIdMapping,
    val arraySlots: Map<VariableDeclaration, ArraySlot> = emptyMap(),
) {
    private val reverseVariableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>> =
        variableMappings.mapValues { (_, mapping) ->
            mapping.entries.associate { (original, actual) -> actual to original }
        }

    fun resolveOriginalVariable(
        holder: Instance,
        actualVariable: VariableDeclaration,
    ): VariableDeclaration {
        val reverse = reverseVariableMappings[holder] ?: sourceError(
            actualVariable,
            "No variable mappings for instance '${holder.name}'",
        )
        return reverse[actualVariable] ?: sourceError(
            actualVariable,
            "Variable '${actualVariable.name}' not found in reverse mapping for instance '${holder.name}'",
        )
    }

}
