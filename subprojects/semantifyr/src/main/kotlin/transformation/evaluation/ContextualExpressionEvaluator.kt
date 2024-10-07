/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation

import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.featureEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.lastChain
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.transitionResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition

class ContextualExpressionEvaluator(
    private val context: Instance
) : ExpressionEvaluator() {

    fun evaluateTransition(expression: Expression): Transition {
        require(expression is ChainReferenceExpression)

        val context = evaluateInstance(expression.dropLast(1))
        return context.transitionResolver.resolveTransition(expression.lastChain())
    }

    fun findFirstValidContext(expression: Expression): Instance {
        try {
            evaluate(expression)

            return context
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be evaluated in the Context tree!"
            }

            return context.parent.contextualEvaluator.findFirstValidContext(expression)
        }
    }

    fun evaluateBottomUp(expression: Expression): DataType {
        try {
            return evaluate(expression)
        } catch (e: RuntimeException) {
            require(context.parent != null) {
                "Expression $expression could not be evaluated in the Context tree!"
            }

            return context.parent.contextualEvaluator.evaluateBottomUp(expression)
        }
    }

    override fun evaluate(expression: Expression) = when (expression) {
        is ChainReferenceExpression -> context.featureEvaluator.evaluate(expression)
        else -> super.evaluate(expression)
    }

}
