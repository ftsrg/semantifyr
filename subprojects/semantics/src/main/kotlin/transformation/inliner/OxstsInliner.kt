/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.optimization.XstsExpressionOptimizer

@Singleton
class OxstsInliner {

    @Inject
    private lateinit var operationInliner: OperationInliner

    @Inject
    private lateinit var callExpressionInliner: ExpressionInliner

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    fun inlineOxsts(inlinedOxsts: InlinedOxsts) {
        operationInliner.inlineOperations(inlinedOxsts.instanceModel.rootInstance, inlinedOxsts.initTransition)
        operationInliner.inlineOperations(inlinedOxsts.instanceModel.rootInstance, inlinedOxsts.mainTransition)

        callExpressionInliner.inlineExpressions(inlinedOxsts.instanceModel.rootInstance, inlinedOxsts.initTransition)
        callExpressionInliner.inlineExpressions(inlinedOxsts.instanceModel.rootInstance, inlinedOxsts.mainTransition)
        callExpressionInliner.inlineExpressions(inlinedOxsts.instanceModel.rootInstance, inlinedOxsts.property)

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.initTransition)
        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.mainTransition)
        xstsExpressionOptimizer.optimize(inlinedOxsts.property)
    }

}
