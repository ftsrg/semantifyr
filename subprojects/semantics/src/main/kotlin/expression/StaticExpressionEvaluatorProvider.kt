/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class StaticExpressionEvaluatorProvider {

    private val cache = mutableMapOf<Instance, StaticExpressionEvaluator>()

    @Inject
    private lateinit var staticExpressionEvaluatorFactory: StaticExpressionEvaluator.Factory

    fun getEvaluator(context: Instance): StaticExpressionEvaluator {
        return cache.getOrPut(context) {
            staticExpressionEvaluatorFactory.create(context)
        }
    }

    fun evaluate(context: Instance, expression: Expression): ExpressionEvaluation {
        return getEvaluator(context).evaluate(expression)
    }

}
