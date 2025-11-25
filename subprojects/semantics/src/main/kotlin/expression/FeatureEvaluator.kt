/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.FeatureSubSettersFinder
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.parentSequence
import hu.bme.mit.semantifyr.semantics.utils.treeSequence

class FeatureEvaluator {

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var featureSubSettersFinder: FeatureSubSettersFinder

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var oppositeHandler: OppositeHandler

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

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
            FeatureKind.CONTAINMENT,
            FeatureKind.CONTAINER -> evaluateContainmentFeature(instance, featureDeclaration)
            FeatureKind.DERIVED -> evaluateDerivedFeature(instance, featureDeclaration)
            FeatureKind.FEATURE -> error("Abstract features can not be evaluated!")
        }
    }

    private fun evaluateReferenceFeature(instance: Instance, featureDeclaration: FeatureDeclaration): ExpressionEvaluation {
        if (featureDeclaration.expression != null) {
            return staticExpressionEvaluatorProvider.evaluate(instance, featureDeclaration.expression)
        }

        val instances = mutableSetOf<Instance>()

        for (feature in featureSubSettersFinder.getSubSetters(instance.domain, featureDeclaration)) {
            val evaluation = evaluateFeature(instance, feature)
            check(evaluation is InstanceEvaluation)
            instances += evaluation.instances
        }

        return InstanceEvaluation(instances)
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
        val opposite = oppositeHandler.getOppositeFeature(featureDeclaration)

        if (opposite == null) {
            error("Only opposite derived features are supported right now!")
        }

        return evaluateOppositeDerivedFeature(instance, opposite)
    }

    private fun evaluateOppositeDerivedFeature(instance: Instance, opposite: FeatureDeclaration): ExpressionEvaluation {
        val rootInstance = instance.parentSequence().last()
        val allInstances = rootInstance.treeSequence()
        val oppositeInstances = allInstances.filter { candidate ->
            val resolved = redefinitionAwareReferenceResolver.resolveOrNull(candidate.domain, opposite) ?: return@filter false
            val evaluator = staticExpressionEvaluatorProvider.getEvaluator(candidate)
            val evaluation = evaluator.evaluate(OxstsFactory.createElementReference(resolved))

            if (evaluation is InstanceEvaluation) {
                evaluation.instances.contains(instance)
            } else {
                false
            }
        }.toSet()

        return InstanceEvaluation(oppositeInstances)
    }

}
