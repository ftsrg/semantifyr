/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element

@Singleton
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
        return operationFlattenerOptimizer.optimize(element)
            || redundantOperationRemoverOptimizer.optimize(element)
            || constantFalseAssumptionPropagatorOptimizer.optimize(element)
            || xstsExpressionOptimizer.optimize(element)
    }

}
