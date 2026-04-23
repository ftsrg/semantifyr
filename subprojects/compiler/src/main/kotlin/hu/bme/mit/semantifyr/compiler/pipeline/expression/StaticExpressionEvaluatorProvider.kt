/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression

@Singleton
class StaticExpressionEvaluatorProvider @Inject constructor(
    private val staticExpressionEvaluatorFactory: StaticExpressionEvaluator.Factory,
) {

    private val cache = mutableMapOf<Instance, StaticExpressionEvaluator>()

    fun getEvaluator(context: Instance): StaticExpressionEvaluator {
        return cache.getOrPut(context) {
            staticExpressionEvaluatorFactory.create(context)
        }
    }

    fun evaluate(context: Instance, expression: Expression): ExpressionEvaluation {
        return getEvaluator(context).evaluate(expression)
    }

}
