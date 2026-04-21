/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

/**
 * Common contract across every compilation phase: the IR being compiled.
 * Implemented by every phase context. Does not imply any shared state beyond
 * the model itself - each phase class is independent.
 */
interface CompilationContext {
    val inlinedOxsts: InlinedOxsts
}

/**
 * Contract for contexts that carry an anchor [rootInstance] suitable for
 * constructing expression evaluators. All post-instantiation contexts provide
 * this; the post-flatten context exposes only the root (no feature tree), which
 * is enough to build evaluators over the flat IR.
 */
interface EvaluableCompilationContext : CompilationContext {
    val rootInstance: Instance
}

/**
 * Initial context: the model exists but instantiation has not run yet.
 * Upgrade to [InstantiatedCompilationContext] via [instantiated].
 */
class CreatedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
) : CompilationContext {

    fun instantiated(instanceTree: InstanceTree): InstantiatedCompilationContext {
        return InstantiatedCompilationContext(inlinedOxsts, instanceTree)
    }

}

/**
 * Post-instantiation context: the instance tree is built, call inlining has not
 * yet run. Upgrade to [InlinedCompilationContext] via [inlined].
 */
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

/**
 * Post-inlining context: transitions and property calls have been inlined. The
 * instance tree is still the authoritative source for evaluator construction.
 * Upgrade to [FlattenedCompilationContext] via [deflated]; the instance tree
 * is discarded at that point.
 */
class InlinedCompilationContext(
    override val inlinedOxsts: InlinedOxsts,
    val instanceTree: InstanceTree,
    val transitionCallTraces: TransitionCallTraceMap,
) : EvaluableCompilationContext {

    override val rootInstance: Instance
        get() = instanceTree.rootInstance

    fun deflated(flatteningInfo: FlatteningInfo): FlattenedCompilationContext {
        return FlattenedCompilationContext(
            inlinedOxsts = inlinedOxsts,
            rootInstance = instanceTree.rootInstance,
            transitionCallTraces = transitionCallTraces,
            flatteningInfo = flatteningInfo,
        )
    }

}

/**
 * Post-flatten context. The IR is strictly flat at this point: all features,
 * containment structure, and instance navigation have been eliminated.
 *
 * The [rootInstance] is retained (not the full [InstanceTree]) as the anchor for
 * constructing expression evaluators. All feature-shaped bookkeeping lives in
 * [flatteningInfo].
 */
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
        val reverse = reverseVariableMappings[holder]
            ?: hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError(
                actualVariable,
                "No variable mappings for instance '${holder.name}'",
            )
        return reverse[actualVariable]
            ?: hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError(
                actualVariable,
                "Variable '${actualVariable.name}' not found in reverse mapping for instance '${holder.name}'",
            )
    }

}
