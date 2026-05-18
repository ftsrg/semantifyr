/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.lang.semantics.FeatureSubSettersFinder
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantElementValueEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration

class CompileTimeElementValueEvaluator @AssistedInject constructor(
    @param:Assisted val instance: Instance,
    private val featureSubSettersFinder: FeatureSubSettersFinder,
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val oppositeHandler: OppositeHandler,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
) : ConstantElementValueEvaluator() {

    private val featureEvaluator = FeatureEvaluator(
        featureSubSettersFinder,
        compileTimeExpressionEvaluatorProvider,
        oppositeHandler,
        redefinitionAwareReferenceResolver,
    )

    override fun visit(element: Element): ExpressionEvaluation {
        if (element is FeatureDeclaration) {
            return featureEvaluator.evaluateFeature(instance, element)
        }

        return super.visit(element)
    }

    interface Factory {
        fun create(instance: Instance): CompileTimeElementValueEvaluator
    }

}
