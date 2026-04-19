/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.TransitionCallTraceBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.InstantiatedPhaseOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class OxstsInliner @Inject constructor(
    private val instantiatedPhaseOptimizer: InstantiatedPhaseOptimizer,
    private val operationCallInlinerProvider: OperationCallInliner.Factory,
    private val expressionCallInlinerProvider: ExpressionCallInliner.Factory,
    private val transitionCallTraceBuilder: TransitionCallTraceBuilder,
) {

    fun inline(instantiatedContext: InstantiatedCompilationContext): InlinedCompilationContext {
        val inlinedOxsts = instantiatedContext.inlinedOxsts
        val instanceTree = instantiatedContext.instanceTree

        inlineOperationCalls(instanceTree.rootInstance, inlinedOxsts.initTransition)
        inlineOperationCalls(instanceTree.rootInstance, inlinedOxsts.mainTransition)
        inlineExpressionCalls(instanceTree.rootInstance, inlinedOxsts.property)

        instantiatedPhaseOptimizer.optimize(instantiatedContext)

        ensureTemporalExpressions(inlinedOxsts)

        return instantiatedContext.inlined(transitionCallTraceBuilder.build())
    }

    private fun ensureTemporalExpressions(inlinedOxsts: InlinedOxsts) {
        if (inlinedOxsts.property.expression !is TemporalOperator) {
            inlinedOxsts.property.expression = OxstsFactory.createAG().also {
                it.body = inlinedOxsts.property.expression
            }
        }

        if (inlinedOxsts.eAllOfType<TemporalOperator>().count() > 1) {
            error("Temporal operators may only appear inside property blocks!")
        }
    }

    private fun inlineOperationCalls(rootInstance: Instance, transition: TransitionDeclaration) {
        val processor = operationCallInlinerProvider.create(rootInstance)

        for (branch in transition.branches) {
            processor.process(branch)
        }
    }

    private fun inlineExpressionCalls(rootInstance: Instance, propertyDeclaration: PropertyDeclaration) {
        val processor = expressionCallInlinerProvider.create(rootInstance)
        processor.process(propertyDeclaration.expression)
    }

}
