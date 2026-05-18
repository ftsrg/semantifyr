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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element

@CompilationScoped
class CompileTimeElementValueEvaluatorProvider
@Inject
constructor(
    private val compileTimeElementValueEvaluatorFactory: CompileTimeElementValueEvaluator.Factory,
) {
    private val cache = mutableMapOf<Instance, CompileTimeElementValueEvaluator>()

    fun getEvaluator(context: Instance): CompileTimeElementValueEvaluator {
        return cache.getOrPut(context) {
            compileTimeElementValueEvaluatorFactory.create(context)
        }
    }

    fun evaluate(
        context: Instance,
        element: Element,
    ): ExpressionEvaluation {
        return getEvaluator(context).evaluate(element)
    }
}
