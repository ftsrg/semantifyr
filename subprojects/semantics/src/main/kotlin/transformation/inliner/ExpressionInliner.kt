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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2
import java.util.*

@Singleton
class ExpressionInliner {

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

    @Inject
    private lateinit var instanceManager: InstanceManager

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
                processorQueue.addAll(0, inlined.findCallExpressions(instance).toList())

                compilationArtifactSaver.commitModelState()
            }
        }
    }

    private fun Element.findCallExpressions(instance: Instance): Sequence<CallSuffixExpression> {
        return eAllOfType<CallSuffixExpression>().filter {
            val calledElement = metaStaticExpressionEvaluatorProvider.evaluate(instance, it.primary)
            calledElement is PropertyDeclaration
        }
    }

    private fun createInlinedExpression(instance: Instance, callSuffixExpression: CallSuffixExpression): Expression {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val propertyReferenceExpression = callSuffixExpression.primary

        val containerInstanceReference = if (propertyReferenceExpression is NavigationSuffixExpression) {
            check(propertyReferenceExpression.primary !is VariableDeclaration) {
                "Variable dispatching is not supported yet!"
            }

            propertyReferenceExpression.primary
        } else {
            OxstsFactory.createSelfReference()
        }

        val containerInstance = evaluator.evaluateSingleInstanceOrNull(containerInstanceReference)

        @Suppress("FoldInitializerAndIfToElvis")
        if (containerInstance == null) {
            error("Container instance of the property is empty!")
        }

        val actualContainerInstanceReference = instanceManager.createReferenceExpression(containerInstance)

        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)

        val property = metaEvaluator.evaluateTyped(PropertyDeclaration::class.java, propertyReferenceExpression)

        // This trick ensures that the expression rewriter can rewrite the passed in expression itself as well
        val expressionHolder = OxstsFactory.createArgument()
        expressionHolder.expression = property.expression.copy()

        expressionRewriter.rewriteExpressionsToContext(expressionHolder.expression, actualContainerInstanceReference)
        expressionRewriter.rewriteExpressionsToCall(expressionHolder.expression, property, callSuffixExpression)

        return expressionHolder.expression
    }

}
