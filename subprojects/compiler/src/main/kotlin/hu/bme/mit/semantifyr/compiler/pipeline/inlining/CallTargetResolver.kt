/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceCollector
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.parentSequence
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

sealed interface CallTarget {

    val containerReferenceExpression: Expression

    data class DirectInstance(
        override val containerReferenceExpression: Expression,
        val containerInstance: Instance,
        val targetDeclaration: NamedElement,
    ) : CallTarget

    data class VariableDispatch(
        override val containerReferenceExpression: Expression,
        val variable: VariableDeclaration,
        val candidateInstances: Set<Instance>,
        val targetDeclaration: NamedElement,
    ) : CallTarget

    data class MissingOptional(
        override val containerReferenceExpression: Expression,
    ) : CallTarget
}

class CallTargetResolver @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val instanceCollector: InstanceCollector,
) {

    fun resolve(callExpression: CallSuffixExpression, instance: Instance): CallTarget {
        val primary = callExpression.primary

        if (primary !is NavigationSuffixExpression) {
            // Bare `b(args)` - self-dispatch.
            val targetDeclaration = (primary as? ElementReference)?.element ?: sourceError(
                callExpression,
                "Unexpected call primary shape: ${primary::class.simpleName}",
            )
            return CallTarget.DirectInstance(
                containerReferenceExpression = OxstsFactory.createSelfReference(),
                containerInstance = instance,
                targetDeclaration = targetDeclaration,
            )
        }

        val holderExpression = primary.primary
        val targetDeclaration = primary.member
        val metaEvaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val holder = metaEvaluator.evaluate(holderExpression)

        if (holder is VariableDeclaration) {
            val candidateInstances = candidateInstancesOf(holder, holderExpression, instance)
            return CallTarget.VariableDispatch(
                containerReferenceExpression = holderExpression,
                variable = holder,
                candidateInstances = candidateInstances,
                targetDeclaration = targetDeclaration,
            )
        }

        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val containerInstance = evaluator.evaluateSingleInstanceOrNull(holderExpression)
        if (containerInstance == null) {
            if (primary.isOptional) {
                return CallTarget.MissingOptional(holderExpression)
            }

            return CallTarget.DirectInstance(holderExpression, instance, targetDeclaration)
        }

        return CallTarget.DirectInstance(
            containerReferenceExpression = holderExpression,
            containerInstance = containerInstance,
            targetDeclaration = targetDeclaration,
        )
    }

    private fun candidateInstancesOf(
        variable: VariableDeclaration,
        variableAccessExpression: Expression,
        instance: Instance,
    ): Set<Instance> {
        if (OxstsUtils.isLoopVariable(variable)) {
            return candidateInstanceOfLoopVariable(variable, instance)
        }

        val domain = variable.typeSpecification?.domain ?: return emptySet()

        return candidateInstancesOf(domain, variableAccessExpression, instance)
    }

    private fun candidateInstanceOfLoopVariable(
        variable: VariableDeclaration,
        instance: Instance,
    ): Set<Instance> {
        val forOp = variable.eContainer() as AbstractForOperation
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val rangeExpr = forOp.rangeExpression
        val instances = mutableSetOf<Instance>()
        if (rangeExpr is ArrayLiteral) {
            for (value in rangeExpr.values) {
                val element = evaluator.evaluate(value)
                if (element is InstanceEvaluation) {
                    instances.addAll(element.instances)
                }
            }
        } else {
            val evaluation = evaluator.evaluate(rangeExpr)
            if (evaluation is InstanceEvaluation) {
                instances.addAll(evaluation.instances)
            }
        }
        return instances
    }

    private fun candidateInstancesOf(
        domain: DomainDeclaration,
        variableAccessExpression: Expression,
        instance: Instance,
    ): Set<Instance> = when (domain) {
        is FeatureDeclaration -> {
            val holderExpression = when (variableAccessExpression) {
                is NavigationSuffixExpression -> variableAccessExpression.primary.copy()
                is ElementReference -> OxstsFactory.createSelfReference()
                else -> sourceError(
                    variableAccessExpression,
                    "Unexpected variable access shape: ${variableAccessExpression::class.simpleName}",
                )
            }
            val navigation = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = holderExpression
                it.member = domain
            }
            val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
            val evaluation = evaluator.evaluate(navigation)
            (evaluation as? InstanceEvaluation)?.instances ?: emptySet()
        }

        is ClassDeclaration -> {
            val rootInstance = instance.parentSequence().last()
            instanceCollector.instancesOfType(rootInstance, domain)
        }

        else -> emptySet()
    }
}
