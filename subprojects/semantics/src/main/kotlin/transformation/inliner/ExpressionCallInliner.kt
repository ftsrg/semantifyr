/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import org.eclipse.xtext.EcoreUtil2
import java.util.*

class ExpressionCallInliner : ExpressionVisitor<Unit>() {

    lateinit var instance: Instance

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    private val processorQueue: ArrayDeque<Expression> = ArrayDeque<Expression>()

    fun process(expression: Expression) {
        visit(expression)
        processAll()
    }

    private fun processAll() {
        while (processorQueue.any()) {
            val next = processorQueue.removeFirst()
            visit(next)
        }
    }

    override fun visit(expression: RangeExpression) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ComparisonOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ArithmeticBinaryOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: BooleanOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ArithmeticUnaryOperator) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: NegationOperator) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: ArrayLiteral) {
        for (index in expression.values.indices.reversed()) {
            processorQueue.addFirst(expression.values[index])
        }
    }

    override fun visit(expression: LiteralInfinity) {
        // NO-OP
    }

    override fun visit(expression: LiteralReal) {
        // NO-OP
    }

    override fun visit(expression: LiteralInteger) {
        // NO-OP
    }

    override fun visit(expression: LiteralString) {
        // NO-OP
    }

    override fun visit(expression: LiteralBoolean) {
        // NO-OP
    }

    override fun visit(expression: LiteralNothing) {
        // NO-OP
    }

    override fun visit(expression: ElementReference) {
        // NO-OP
    }

    override fun visit(expression: SelfReference) {
        // NO-OP
    }

    override fun visit(expression: NavigationSuffixExpression) {
        // NO-OP
    }

    override fun visit(expression: CallSuffixExpression) {
        val inlined = createInlinedExpression(instance, expression)
        EcoreUtil2.replace(expression, inlined)
        processorQueue.addFirst(inlined)

        compilationStateManager.commitModelState()
    }

    override fun visit(expression: IndexingSuffixExpression) {
        processorQueue.addFirst(expression.index)
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
