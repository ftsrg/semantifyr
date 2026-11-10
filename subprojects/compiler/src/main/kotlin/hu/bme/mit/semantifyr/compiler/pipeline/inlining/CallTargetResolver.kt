/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

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
        val branches: List<DispatchBranch>,
        val targetDeclaration: NamedElement,
    ) : CallTarget

    data class MissingOptional(
        override val containerReferenceExpression: Expression,
    ) : CallTarget
}

data class DispatchBranch(
    val candidateInstance: Instance,
    val containerInstance: Instance,
)

data class RuntimeHolder(
    val holderExpression: Expression,
    val variable: VariableDeclaration,
    val candidateInstances: Set<Instance>,
)

class CallTargetResolver @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val instanceCollector: InstanceCollector,
    private val localVariableManager: LocalVariableManager,
    private val instanceReferenceProvider: InstanceReferenceProvider,
) {

    fun resolve(callExpression: CallSuffixExpression, instance: Instance): CallTarget {
        val primary = callExpression.primary

        if (primary !is NavigationSuffixExpression) {
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
        val runtimeHolder = runtimeHolderOf(primary, instance)

        if (runtimeHolder != null) {
            return CallTarget.VariableDispatch(
                containerReferenceExpression = runtimeHolder.holderExpression,
                variable = runtimeHolder.variable,
                branches = dispatchBranchesOf(runtimeHolder, holderExpression, instance),
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

    // the innermost holder, so that a chain through two runtime holders dispatches on the first and then on each candidate
    fun runtimeHolderOf(expression: Expression, instance: Instance): RuntimeHolder? {
        val navigations = generateSequence(expression) {
            (it as? PostfixUnaryExpression)?.primary
        }.filterIsInstance<NavigationSuffixExpression>().filter {
            it.member.eContainer() !is RecordDeclaration
        }.toList().asReversed()

        val metaEvaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        for (navigation in navigations) {
            val holderExpression = navigation.primary
            val variableAccessExpression = (holderExpression as? IndexingSuffixExpression)?.primary ?: holderExpression
            val variable = metaEvaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, variableAccessExpression) ?: continue
            return RuntimeHolder(
                holderExpression = holderExpression,
                variable = variable,
                candidateInstances = candidateInstancesOf(variable, variableAccessExpression, instance),
            )
        }
        return null
    }

    fun replaceHolder(
        expression: Expression,
        runtimeHolder: RuntimeHolder,
        replacement: Expression,
    ): Expression {
        if (expression === runtimeHolder.holderExpression) {
            return replacement
        }
        val depth = generateSequence(expression) {
            (it as? PostfixUnaryExpression)?.primary
        }.indexOfFirst {
            it === runtimeHolder.holderExpression
        }
        check(depth > 0) {
            "The runtime holder is not a prefix of the expression"
        }

        val expressionCopy = expression.copy()
        var holderCopy = expressionCopy
        repeat(depth) {
            holderCopy = (holderCopy as PostfixUnaryExpression).primary
        }
        EcoreUtil2.replace(holderCopy, replacement)
        return expressionCopy
    }

    private fun dispatchBranchesOf(
        runtimeHolder: RuntimeHolder,
        holderExpression: Expression,
        instance: Instance,
    ): List<DispatchBranch> {
        if (runtimeHolder.holderExpression === holderExpression) {
            return runtimeHolder.candidateInstances.map {
                DispatchBranch(it, it)
            }
        }
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        return runtimeHolder.candidateInstances.map {
            val containerExpression = replaceHolder(holderExpression, runtimeHolder, instanceReferenceProvider.getReference(it))
            DispatchBranch(it, evaluator.evaluateSingleInstance(containerExpression))
        }
    }

    private fun candidateInstancesOf(
        variable: VariableDeclaration,
        variableAccessExpression: Expression,
        instance: Instance,
    ): Set<Instance> {
        if (OxstsUtils.isLoopVariable(variable)) {
            return candidateInstanceOfLoopVariable(variable, instance)
        }

        if (variable is LocalVarDeclarationOperation) {
            return localVariableManager.instanceDomainOf(variable).orEmpty()
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
