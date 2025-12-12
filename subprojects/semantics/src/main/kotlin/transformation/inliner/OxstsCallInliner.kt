/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class OxstsCallInliner {

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var operationCallInlinerProvider: OperationCallInliner.Factory

    @Inject
    private lateinit var expressionCallInlinerProvider: ExpressionCallInliner.Factory

    fun inlineCalls(inlinedOxsts: InlinedOxsts) {
        inlineOperationCalls(inlinedOxsts.rootInstance, inlinedOxsts.initTransition)
        inlineOperationCalls(inlinedOxsts.rootInstance, inlinedOxsts.mainTransition)
        inlineExpressionCalls(inlinedOxsts.rootInstance, inlinedOxsts.property)

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts)
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
