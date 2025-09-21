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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager

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

    override fun compute(expression: ElementReference): ExpressionEvaluation {
        return evaluateElement(expression.element)
    }

    override fun compute(expression: SelfReference): ExpressionEvaluation {
        return InstanceEvaluation(instance)
    }

    override fun compute(expression: NavigationSuffixExpression): ExpressionEvaluation {
        val instance = evaluateSingleInstance(expression.primary)
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        return evaluator.evaluateElement(expression.member)
    }

    override fun compute(expression: CallSuffixExpression): ExpressionEvaluation {
        error("TODO: implement")
        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
        val calledElement = metaEvaluator.evaluate(expression.primary)
        // FIXME: evaluate the called element
        return super.compute(expression)
    }

    override fun compute(expression: IndexingSuffixExpression): ExpressionEvaluation {
        TODO("TODO: implement")
    }

    private fun evaluateElement(element: NamedElement): ExpressionEvaluation {
        val resolvedElement = redefinitionAwareReferenceResolver.resolve(instance, element)

        if (resolvedElement is FeatureDeclaration) {
            return InstanceEvaluation(instanceManager.instancesAt(instance, resolvedElement))
        }

        error("This expression is not resolvable! (probably a meta-expression?)")
    }

    fun evaluateInstances(expression: Expression): List<Instance> {
        return evaluateInstancesOrNull(expression) ?: error("This expression is not evaluable to instances!")
    }

    fun evaluateInstancesOrNull(expression: Expression): List<Instance>? {
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

