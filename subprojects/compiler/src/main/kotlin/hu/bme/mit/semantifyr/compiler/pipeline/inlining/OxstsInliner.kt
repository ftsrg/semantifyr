/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.InlinedPhaseOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import org.eclipse.xtext.EcoreUtil2

class OxstsInliner @Inject constructor(
    private val inlinedPhaseOptimizer: InlinedPhaseOptimizer,
    private val operationCallInlinerProvider: OperationCallInliner.Factory,
    private val expressionCallInlinerProvider: ExpressionCallInliner.Factory,
    private val transitionCallTraceTransformer: TransitionCallTraceTransformer,
) {

    private val logger by loggerFactory()

    fun inline(instantiatedContext: InstantiatedCompilationContext): InlinedCompilationContext {
        val inlinedOxsts = instantiatedContext.inlinedOxsts
        val instanceTree = instantiatedContext.instanceTree

        logger.debug { "Inlining init transition" }
        inlineOperationCalls(instanceTree.rootInstance, inlinedOxsts.initTransition)

        logger.debug { "Inlining main transition" }
        inlineOperationCalls(instanceTree.rootInstance, inlinedOxsts.mainTransition)

        logger.debug { "Inlining property expression" }
        inlineExpressionCalls(instanceTree.rootInstance, inlinedOxsts.property)

        logger.debug { "Finalizing transition call tracer state" }
        val transitionCallTraces = transitionCallTraceTransformer.finalize(inlinedOxsts)

        logger.info { "Running post-inlining optimizers" }
        inlinedPhaseOptimizer.optimize(instantiatedContext)

        ensureTemporalExpressions(inlinedOxsts)

        return instantiatedContext.inlined(transitionCallTraces)
    }

    private fun ensureTemporalExpressions(inlinedOxsts: InlinedOxsts) {
        val rootExpression = inlinedOxsts.property.expression
        if (rootExpression !is TemporalOperator) {
            sourceError(
                rootExpression,
                "Property body must have a temporal operator (`AG` or `EF`) at the top level.",
            )
        }

        val incorrectTemporalOperators = inlinedOxsts.eAllOfType<TemporalOperator>().filter {
            it !== rootExpression
        }.toList()

        if (incorrectTemporalOperators.isNotEmpty()) {
            val message = buildString {
                appendLine("There are temporal operators that are not the root of the main prop's expression:")
                for (incorrectTemporalOperator in incorrectTemporalOperators) {
                    appendLine("${SourceLocation.prefixFor(incorrectTemporalOperator)}")
                }
            }
            error(message)
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
