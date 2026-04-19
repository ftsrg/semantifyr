/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance

open class CompilationContext(
    val inlinedOxsts: InlinedOxsts,
) {

    fun instantiated(instanceTree: InstanceTree): InstantiatedCompilationContext {
        return InstantiatedCompilationContext(inlinedOxsts, instanceTree)
    }

}

open class InstantiatedCompilationContext(
    inlinedOxsts: InlinedOxsts,
    val instanceTree: InstanceTree,
) : CompilationContext(inlinedOxsts) {

    fun inlined(transitionCallTraces: TransitionCallTraceMap): InlinedCompilationContext {
        return InlinedCompilationContext(inlinedOxsts, instanceTree, transitionCallTraces)
    }

}

open class InlinedCompilationContext(
    inlinedOxsts: InlinedOxsts,
    instanceTree: InstanceTree,
    val transitionCallTraces: TransitionCallTraceMap,
) : InstantiatedCompilationContext(inlinedOxsts, instanceTree) {

    fun deflated(flatteningInfo: FlatteningInfo): FlattenedCompilationContext {
        return FlattenedCompilationContext(inlinedOxsts, instanceTree, transitionCallTraces, flatteningInfo)
    }

}

class FlattenedCompilationContext(
    inlinedOxsts: InlinedOxsts,
    instanceTree: InstanceTree,
    transitionCallTraces: TransitionCallTraceMap,
    val flatteningInfo: FlatteningInfo,
) : InlinedCompilationContext(inlinedOxsts, instanceTree, transitionCallTraces)

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
        val reverse = reverseVariableMappings[holder] ?: error("No variable mappings for instance")
        return reverse[actualVariable] ?: error("Variable '${actualVariable.name}' not found in reverse mapping")
    }

}
