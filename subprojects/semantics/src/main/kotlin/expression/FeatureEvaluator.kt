/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.FeatureSubSettersFinder

@CompilationScoped
class FeatureEvaluator {

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var featureSubSettersFinder: FeatureSubSettersFinder

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    fun evaluateFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        if (OxstsUtils.isDataFeature(featureDeclaration)) {
            return evaluateDataFeature(instance, featureDeclaration)
        }

        return evaluateInstanceFeature(instance, featureDeclaration)
    }

    private fun evaluateDataFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        return staticExpressionEvaluatorProvider.evaluate(instance, featureDeclaration.expression)
    }

    private fun evaluateInstanceFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        return when (featureDeclaration.kind) {
            FeatureKind.REFERENCE -> evaluateReferenceFeature(instance, featureDeclaration)
            FeatureKind.CONTAINMENT -> evaluateContainmentFeature(instance, featureDeclaration)
            FeatureKind.CONTAINER -> evaluateContainmentFeature(instance, featureDeclaration)
            FeatureKind.DERIVED -> evaluateDerivedFeature(instance, featureDeclaration)
            FeatureKind.FEATURE -> error("Abstract features can not be evaluated!")
        }
    }

    private fun evaluateReferenceFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        return staticExpressionEvaluatorProvider.evaluate(instance, featureDeclaration.expression)
    }

    private fun evaluateContainmentFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        val instances = instanceManager.instancesAt(instance, featureDeclaration).toMutableSet()

        for (feature in featureSubSettersFinder.getSubSetters(instance.domain, featureDeclaration)) {
            val evaluation = evaluateFeature(instance, feature)
            check(evaluation is InstanceEvaluation)
            instances += evaluation.instances
        }

        return InstanceEvaluation(instances)
    }

    private fun evaluateDerivedFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        return InstanceEvaluation(setOf())
    }

}
