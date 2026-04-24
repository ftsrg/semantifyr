/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

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

    fun inlined(transitionCallTraces: TransitionCallTraceMap): InlinedCompilationContext {
        return InlinedCompilationContext(inlinedOxsts, instanceTree, transitionCallTraces)
    }

}

class InlinedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    val instanceTree: InstanceTree,
    val transitionCallTraces: TransitionCallTraceMap,
) : EvaluableCompilationContext {

    override val rootInstance: Instance
        get() = instanceTree.rootInstance

    fun flattened(flatteningInfo: FlatteningInfo): FlattenedCompilationContext {
        return FlattenedCompilationContext(
            inlinedOxsts = inlinedOxsts,
            rootInstance = instanceTree.rootInstance,
            transitionCallTraces = transitionCallTraces,
            flatteningInfo = flatteningInfo,
        )
    }

}

class FlattenedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    override val rootInstance: Instance,
    val transitionCallTraces: TransitionCallTraceMap,
    val flatteningInfo: FlatteningInfo,
) : EvaluableCompilationContext

class FlatteningInfo(
    val variableHolders: Map<VariableDeclaration, Instance>,
    val variableInstanceDomains: Map<VariableDeclaration, Set<Instance>>,
    val variableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    val instanceIdMapping: InstanceIdMapping,
) {
    private val reverseVariableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>> =
        variableMappings.mapValues { (_, mapping) ->
            mapping.entries.associate { (original, actual) -> actual to original }
        }

    fun resolveOriginalVariable(holder: Instance, actualVariable: VariableDeclaration): VariableDeclaration {
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
