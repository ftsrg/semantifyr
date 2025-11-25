/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantElementValueEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance

class StaticElementValueEvaluator @AssistedInject constructor(
    @param:Assisted val instance: Instance
) : ConstantElementValueEvaluator() {

    @Inject
    private lateinit var featureEvaluator: FeatureEvaluator

    override fun visit(element: Element): ExpressionEvaluation {
        if (element is FeatureDeclaration) {
            return featureEvaluator.evaluateFeature(instance, element)
        }

        return super.visit(element)
    }

    interface Factory {
        fun create(instance: Instance): StaticElementValueEvaluator
    }

}
