/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.FeatureSubSettersFinder
import hu.bme.mit.semantifyr.semantics.utils.parentSequence

@CompilationScoped
class FeatureEvaluator {

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var featureSubSettersFinder: FeatureSubSettersFinder

    @Inject
    private lateinit var domainMemberCollectionProvider: DomainMemberCollectionProvider

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    private fun evaluateFeatureExpression(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        val validContext = findValidContext(instance, featureDeclaration.expression)
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(validContext)

        return evaluator.evaluate(featureDeclaration.expression)
    }

    fun evaluateFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        val instances = mutableSetOf<Instance>()

        if (featureDeclaration.expression != null) {
            return evaluateFeatureExpression(instance, featureDeclaration)
        }

        instances += instanceManager.instancesAt(instance, featureDeclaration)

        for (feature in featureSubSettersFinder.getSubSetters(instance.domain, featureDeclaration)) {
            val evaluation = evaluateFeature(instance, feature)
            check(evaluation is InstanceEvaluation)
            instances += evaluation.instances
        }

        return InstanceEvaluation(instances)
    }

    private fun findValidContext(instance: Instance, expression: Expression): Instance {
        // TODO: What if this is an array, and there are internal expressions? This should be done better...
        if (expression !is ReferenceExpression) {
            return instance
        }

        val innerMostExpression = expression.innerMostElementReference()

        @Suppress("SimplifyBooleanWithConstants") // more readable this way
        if (OxstsUtils.isReferenceContextual(innerMostExpression) == false) {
            return instance.parentSequence().last()
        }

        for (candidate in instance.parentSequence()) {
            val members = domainMemberCollectionProvider.getMemberCollection(candidate.domain)

            if (members.resolveElementOrNull(innerMostExpression.element as Declaration) != null) {
                return candidate
            }
        }

        error("No valid context found in instance tree!")
    }

    private tailrec fun ReferenceExpression.innerMostElementReference(): ElementReference {
        return when (this) {
            is ElementReference -> this
            is PostfixUnaryExpression -> {
                val prim = primary
                if (prim is ReferenceExpression) {
                    prim.innerMostElementReference()
                } else {
                    error("Unexpected expression!")
                }
            }
            else -> error("Unexpected expression!")
        }
    }

}
