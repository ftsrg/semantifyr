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
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.FeatureSubSettersFinder
import hu.bme.mit.semantifyr.semantics.utils.isReferenceContextual
import hu.bme.mit.semantifyr.semantics.utils.parentSequence

class StaticExpressionEvaluator : ConstantExpressionEvaluator() {

    lateinit var instance: Instance

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var featureSubSettersFinder: FeatureSubSettersFinder

    @Inject
    private lateinit var domainMemberCalculator: DomainMemberCalculator

    override fun visit(expression: ComparisonOperator): ExpressionEvaluation {
        val left = evaluate(expression.getLeft())
        val right = evaluate(expression.getRight())

        if (left is InstanceEvaluation && right is InstanceEvaluation) {
            return when (expression.getOp()) {
                ComparisonOp.EQ -> BooleanEvaluation(left.instances.containsAll(right.instances))
                ComparisonOp.NOT_EQ -> BooleanEvaluation(!left.instances.containsAll(right.instances))
                else -> error("Unsupported operator!")
            }
        }

        return super.visit(expression)
    }

    override fun visit(expression: ElementReference): ExpressionEvaluation {
        return evaluateElement(expression.element)
    }

    override fun visit(expression: SelfReference): ExpressionEvaluation {
        return InstanceEvaluation(instance)
    }

    override fun visit(expression: LiteralNothing?): ExpressionEvaluation {
        return InstanceEvaluation(emptySet())
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
        error("TODO: implement")
        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
        val calledElement = metaEvaluator.evaluate(expression.primary)
        // FIXME: evaluate the called element
        return super.visit(expression)
    }

    override fun visit(expression: IndexingSuffixExpression): ExpressionEvaluation {
        TODO("TODO: implement")
    }

    private fun evaluateElement(element: NamedElement): ExpressionEvaluation {
        val resolvedElement = redefinitionAwareReferenceResolver.resolve(instance, element)

        if (resolvedElement !is FeatureDeclaration) {
            error("This expression is not resolvable! (probably a meta-expression?)")
        }

        if (resolvedElement.expression != null) {
            return evaluateFeatureExpression(resolvedElement)
        }

        return evaluateFeature(resolvedElement)
    }

    private fun evaluateFeatureExpression(featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        val validContext = findValidContext(instance, featureDeclaration.expression)
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(validContext)

        return evaluator.evaluate(featureDeclaration.expression)
    }

    private fun evaluateFeature(featureDeclaration: FeatureDeclaration): InstanceEvaluation {
        val instances = mutableSetOf<Instance>()

        instances += instanceManager.instancesAt(instance, featureDeclaration)

        for (feature in featureSubSettersFinder.getSubSetters(instance.domain, featureDeclaration)) {
            instances += evaluateFeature(feature).instances
        }

        return InstanceEvaluation(instances)
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

    private fun findValidContext(instance: Instance, expression: Expression): Instance {
        // TODO: What if this is an array, and there are internal expressions? This should be done better...
        if (expression !is ReferenceExpression) {
            return instance
        }

        val innerMostExpression = expression.innerMostElementReference()

        @Suppress("SimplifyBooleanWithConstants") // more readable this way
        if (isReferenceContextual(innerMostExpression) == false) {
            return instance
        }

        for (candidate in instance.parentSequence()) {
            val members = domainMemberCalculator.getMemberCollection(candidate.domain)

            if (members.declarations.contains(innerMostExpression.element)) {
                return candidate
            }
        }

        error("No valid context found in instance tree!")
    }

    private tailrec fun Expression.innerMostElementReference(): ElementReference {
        return when (this) {
            is ElementReference -> this
            is PostfixUnaryExpression -> primary.innerMostElementReference()
            else -> error("Unsupported expression!")
        }
    }

}
