/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.NothingEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference

class StaticExpressionEvaluator : ConstantExpressionEvaluator() {

    lateinit var instance: Instance

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    @Inject
    private lateinit var featureEvaluator: FeatureEvaluator

    override fun visit(expression: ComparisonOperator): ExpressionEvaluation {
        val left = evaluate(expression.getLeft())
        val right = evaluate(expression.getRight())

        if (left is InstanceEvaluation) {
            if (right is InstanceEvaluation) {
                return when (expression.getOp()) {
                    ComparisonOp.EQ -> BooleanEvaluation((left.instances - right.instances).isEmpty())
                    ComparisonOp.NOT_EQ -> BooleanEvaluation(!(left.instances - right.instances).isEmpty())
                    else -> error("Unsupported operator!")
                }
            }

            if (right is NothingEvaluation) {
                return when (expression.getOp()) {
                    ComparisonOp.EQ -> BooleanEvaluation(left.instances.isEmpty())
                    ComparisonOp.NOT_EQ -> BooleanEvaluation(left.instances.any())
                    else -> error("Unsupported operator!")
                }
            }
        }

        if (right is InstanceEvaluation) {
            if (left is NothingEvaluation) {
                return when (expression.getOp()) {
                    ComparisonOp.EQ -> BooleanEvaluation(right.instances.isEmpty())
                    ComparisonOp.NOT_EQ -> BooleanEvaluation(right.instances.any())
                    else -> error("Unsupported operator!")
                }
            }
        }

        return super.visit(expression)
    }

    override fun visit(expression: SelfReference): ExpressionEvaluation {
        return InstanceEvaluation(instance)
    }

    override fun visit(expression: NavigationSuffixExpression): ExpressionEvaluation {
        val instance = evaluateInstances(expression.primary)

        if (instance.isEmpty()) {
            if (expression.isOptional) {
                return InstanceEvaluation(setOf())
            }

            error("The left side is evaluated to an empty evaluation!")
        }

        if (instance.size != 1) {
            error("Left hand side contains more than a single instance!")
        }

        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance.single())
        return evaluator.evaluateElement(expression.member)
    }

    override fun visit(expression: CallSuffixExpression): ExpressionEvaluation {
        TODO("TODO: implement")
    }

    override fun visit(expression: IndexingSuffixExpression): ExpressionEvaluation {
        TODO("TODO: implement")
    }

    override fun evaluateElement(element: NamedElement): ExpressionEvaluation {
        if (element.eIsProxy()) {
            throw IllegalStateException("Element could not be resolved!");
        }

        val resolvedElement = redefinitionAwareReferenceResolver.resolve(instance.domain, element)

        if (resolvedElement is FeatureDeclaration) {
            return featureEvaluator.evaluateFeature(instance, resolvedElement)
        }

        return super.evaluateElement(resolvedElement)
    }

    fun evaluateInstances(expression: Expression): Set<Instance> {
        return evaluateInstancesOrNull(expression) ?: error("This expression is not evaluable to instances!")
    }

    fun evaluateInstancesOrNull(expression: Expression): Set<Instance>? {
        val evaluation = evaluate(expression)

        if (evaluation is InstanceEvaluation) {
            return evaluation.instances
        }

        return null
    }

    fun evaluateSingleInstance(expression: Expression): Instance {
        return evaluateSingleInstanceOrNull(expression) ?: error("This expression is not evaluable to a single instance!")
    }

    fun evaluateSingleInstanceOrNull(expression: Expression): Instance? {
        val evaluation = evaluate(expression)

        if (evaluation is InstanceEvaluation) {
            return evaluation.instances.singleOrNull()
        }

        return null
    }

    fun evaluateBoolean(expression: Expression): Boolean {
        val evaluation = evaluate(expression)

        if (evaluation is BooleanEvaluation) {
            return evaluation.value
        }

        error("This expression is not evaluable to boolean!")
    }

}
