/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2
import java.util.*

@Singleton
class ExpressionInliner {

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    fun inlineExpressions(instance: Instance, transition: TransitionDeclaration) {
        for (branch in transition.branches) {
            inlineCallExpressions(instance, branch)
        }
    }

    fun inlineExpressions(instance: Instance, property: PropertyDeclaration) {
        inlineCallExpressions(instance, property.expression)
    }

    private fun inlineCallExpressions(instance: Instance, element: Element) {

        val processorQueue = LinkedList<Expression>()

        processorQueue += element.findCallExpressions(instance)

        while (processorQueue.any()) {
            val expression = processorQueue.removeFirst()

            if (expression is CallSuffixExpression) {
                val inlined = createInlinedExpression(instance, expression)
                EcoreUtil2.replace(expression, inlined)
                processorQueue += inlined.findCallExpressions(instance)
            }
        }
    }

    private fun Element.findCallExpressions(instance: Instance) = eAllOfType<CallSuffixExpression>().filter {
        val calledElement = metaStaticExpressionEvaluatorProvider.evaluate(instance, it.primary)
        calledElement is PropertyDeclaration
    }

    private fun createInlinedExpression(instance: Instance, callSuffixExpression: CallSuffixExpression): Expression {
        // TODO: implement argument support
        require(callSuffixExpression.arguments.isEmpty()) {
            "Property inline with arguments is not yet implemented"
        }

        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
        val property = evaluator.evaluateTyped(PropertyDeclaration::class.java, callSuffixExpression.primary)
        return property.expression.copy()
    }

}
