/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceCollector
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

/**
 * Classifies the "who does this `inline a.b(args)` apply to?" question and
 * carries the grammar-level target declaration so the caller does not need
 * to meta-evaluate the call primary itself (which would fail for the
 * variable-dispatch case, where the primary is not constant-evaluable).
 *
 * - `a` missing (the call is a bare `b(args)`): self-dispatch, [DirectInstance]
 *   on the current instance.
 * - `a` resolves to a single concrete instance (e.g. a feature navigation):
 *   [DirectInstance] with the callsite's reference expression reused as the
 *   container reference.
 * - `a` resolves to a variable whose type is feature- or class-valued:
 *   [VariableDispatch] - the caller must expand the call across every
 *   instance in the variable's domain.
 * - `a` is an optional navigation that resolves to nothing at this callsite:
 *   [MissingOptional] - the caller typically replaces the call with an empty
 *   operation.
 *
 * `targetDeclaration` on [DirectInstance] and [VariableDispatch] is the
 * grammar-level target (before redefinition resolution); callers should
 * redefinition-resolve against the concrete container instance's domain.
 */
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

/**
 * Shared lookup used by both the operation- and expression-level call
 * inliners: given a `primary(args)` call, determine whether the target
 * resolves to self, to a single instance, to a variable that needs
 * dispatching, or to a missing optional navigation. Also extracts the
 * grammar-level target declaration (transition / property / etc.) from the
 * callsite primary so the caller can redefinition-resolve it per candidate
 * instance without ever meta-evaluating a non-constant primary.
 */
class CallTargetResolver @Inject constructor(
    private val staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
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
        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
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

        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val containerInstance = evaluator.evaluateSingleInstanceOrNull(holderExpression)
        if (containerInstance == null) {
            if (primary.isOptional) {
                return CallTarget.MissingOptional(holderExpression)
            }
            // Non-variable, non-instance, non-optional. Fall through with an
            // empty DirectInstance; the caller reports the real error where
            // it has the richer context.
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
        val domain = variable.typeSpecification?.domain ?: return emptySet()
        return when (domain) {
            is FeatureDeclaration -> {
                // Features are only meaningful in the context of an instance.
                // Build `<holderOfVariable>.feature` so the static evaluator
                // walks the real instance tree. The holder of the variable
                // is the thing left of the variable in its access expression
                // (e.g. `root.dispatcher.current` -> holder = `root.dispatcher`);
                // for bare variable access (`current`) it is self.
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
                val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
                val evaluation = evaluator.evaluate(navigation)
                (evaluation as? InstanceEvaluation)?.instances ?: emptySet()
            }
            is ClassDeclaration -> {
                val rootInstance = instance.root()
                instanceCollector.instancesOfType(rootInstance, domain)
            }
            else -> emptySet()
        }
    }

    private fun Instance.root(): Instance {
        var current = this
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }
}
