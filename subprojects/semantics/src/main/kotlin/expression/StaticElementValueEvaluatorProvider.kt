/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class StaticElementValueEvaluatorProvider {

    private val cache = mutableMapOf<Instance, StaticElementValueEvaluator>()

    @Inject
    private lateinit var staticExpressionEvaluatorFactory: StaticElementValueEvaluator.Factory

    fun getEvaluator(context: Instance): StaticElementValueEvaluator {
        return cache.getOrPut(context) {
            staticExpressionEvaluatorFactory.create(context)
        }
    }

    fun evaluate(context: Instance, element: Element): ExpressionEvaluation {
        return getEvaluator(context).evaluate(element)
    }

}
