/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation.optimization.patterns.expression

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOperator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ConstantFoldingPattern(
    private val evaluator: ConstantExpressionEvaluatorProvider,
    private val transformer: ConstantExpressionEvaluationTransformer,
) : OptimizationPattern {

    private val folded = mutableSetOf<Expression>()

    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is OperatorExpression) return false
        if (element is UnaryOperator && element.body is LiteralInteger) return false
        if (element in folded) return false

        val evaluation = try {
            evaluator.evaluate(element)
        } catch (_: Exception) {
            return false
        }
        val constant = transformer.transformEvaluation(evaluation)
        val parent = element.eContainer() ?: return false

        EcoreUtil2.replace(element, constant)
        folded += constant
        worklist.add(parent)
        return true
    }

}
