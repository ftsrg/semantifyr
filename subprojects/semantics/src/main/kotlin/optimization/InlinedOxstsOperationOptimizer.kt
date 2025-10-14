/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class InlinedOxstsOperationOptimizer : AbstractLoopedOptimizer<Element>() {

    @Inject
    private lateinit var redundantOperationRemoverOptimizer: RedundantOperationRemoverOptimizer

    @Inject
    private lateinit var operationFlattenerOptimizer: OperationFlattenerOptimizer

    @Inject
    private lateinit var constantFalseAssumptionPropagatorOptimizer: ConstantFalseAssumptionPropagatorOptimizer

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    override fun doOptimizationStep(element: Element): Boolean {
        return constantFalseAssumptionPropagatorOptimizer.optimize(element)
            || operationFlattenerOptimizer.optimize(element)
            || redundantOperationRemoverOptimizer.optimize(element)
            || xstsExpressionOptimizer.optimize(element)
    }

}
