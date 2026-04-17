/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation.optimization

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped

@CompilationScoped
class InlinedOxstsOperationOptimizer @Inject constructor(
    private val redundantOperationRemoverOptimizer: RedundantOperationRemoverOptimizer,
    private val operationFlattenerOptimizer: OperationFlattenerOptimizer,
    private val constantFalseAssumptionPropagatorOptimizer: ConstantFalseAssumptionPropagatorOptimizer,
    private val expressionOptimizer: ExpressionOptimizer,
    private val variableOptimizer: VariableOptimizer,
) : AbstractLoopedOptimizer<InlinedOxsts>() {

    override fun doOptimizationStep(element: InlinedOxsts): Boolean {
        return constantFalseAssumptionPropagatorOptimizer.optimize(element)
            || operationFlattenerOptimizer.optimize(element)
            || redundantOperationRemoverOptimizer.optimize(element)
            || expressionOptimizer.optimize(element)
            || variableOptimizer.optimize(element)
    }

}
