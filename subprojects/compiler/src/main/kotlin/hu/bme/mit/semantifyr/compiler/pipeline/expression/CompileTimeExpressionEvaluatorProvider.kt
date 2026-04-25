/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression

@CompilationScoped
class CompileTimeExpressionEvaluatorProvider @Inject constructor(
    private val compileTimeExpressionEvaluatorFactory: CompileTimeExpressionEvaluator.Factory,
) {

    private val cache = mutableMapOf<Instance, CompileTimeExpressionEvaluator>()

    fun getEvaluator(context: Instance): CompileTimeExpressionEvaluator {
        return cache.getOrPut(context) {
            compileTimeExpressionEvaluatorFactory.create(context)
        }
    }

    fun evaluate(
        context: Instance,
        expression: Expression,
    ): ExpressionEvaluation {
        return getEvaluator(context).evaluate(expression)
    }

}
