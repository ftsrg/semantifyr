/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
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
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    fun inlineExpressions(instance: Instance, transition: TransitionDeclaration) {
        for (branch in transition.branches) {
            inlineCallExpressions(instance, branch)
        }
    }

    fun inlineExpressions(instance: Instance, property: PropertyDeclaration) {
        inlineCallExpressions(instance, property.expression)
    }

    private fun inlineCallExpressions(instance: Instance, element: Element) {
        val processorQueue = LinkedList<Element?>()

        processorQueue += element

        while (processorQueue.any()) {
            val nextElement = processorQueue.removeFirst()

            when (nextElement) {
                is SequenceOperation -> processorQueue.addAll(0, nextElement.steps)
                is ChoiceOperation -> {
                    processorQueue.add(0, nextElement.`else`)
                    processorQueue.addAll(0, nextElement.branches)
                }
                is ForOperation -> processorQueue.add(0, nextElement.body)
                is IfOperation -> {
                    processorQueue.add(0, nextElement.`else`)
                    processorQueue.add(0, nextElement.body)
                }
                is HavocOperation -> processorQueue.add(0, nextElement.reference)
                is AssumptionOperation -> processorQueue.add(0, nextElement.expression)
                is AssignmentOperation -> {
                    processorQueue.add(0, nextElement.reference)
                    processorQueue.add(0, nextElement.expression)
                }

                is BinaryOperator -> {
                    processorQueue.add(0, nextElement.left)
                    processorQueue.add(0, nextElement.right)
                }
                is UnaryOperator -> processorQueue.add(0, nextElement.body)

                is ArrayLiteral -> processorQueue.addAll(0, nextElement.values)
                is NavigationSuffixExpression -> processorQueue.add(0, nextElement.primary)
                is IndexingSuffixExpression -> {
                    processorQueue.add(0, nextElement.primary)
                    processorQueue.add(0, nextElement.index)
                }

                is CallSuffixExpression -> {
                    val inlined = createInlinedExpression(instance, nextElement)
                    EcoreUtil2.replace(nextElement, inlined)
                    processorQueue.add(0, inlined)

                    compilationArtifactSaver.commitModelState()
                }
            }
        }
    }

    private fun createInlinedExpression(instance: Instance, callSuffixExpression: CallSuffixExpression): Expression {
        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val propertyReferenceExpression = callSuffixExpression.primary

        val containerInstanceReference = if (propertyReferenceExpression is NavigationSuffixExpression) {
            val transitionHolder = metaEvaluator.evaluate(propertyReferenceExpression.primary)
            check(transitionHolder !is VariableDeclaration) {
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

        val actualContainerInstanceReference = instanceReferenceProvider.getReference(containerInstance)

        val property = metaEvaluator.evaluateTyped(PropertyDeclaration::class.java, propertyReferenceExpression)

        // This trick ensures that the expression rewriter can rewrite the passed in expression itself as well
        val expressionHolder = OxstsFactory.createArgument()
        expressionHolder.expression = property.expression.copy()

        expressionRewriter.rewriteExpressionsToContext(expressionHolder.expression, actualContainerInstanceReference)
        expressionRewriter.rewriteExpressionsToCall(expressionHolder.expression, property, callSuffixExpression)

        return expressionHolder.expression
    }

}
