/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class MetaStaticExpressionEvaluatorProvider {

    private val cache = mutableMapOf<Instance, MetaStaticExpressionEvaluator>()

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: Provider<MetaStaticExpressionEvaluator>

    fun getEvaluator(context: Instance): MetaStaticExpressionEvaluator {
        return cache.getOrPut(context) {
            createEvaluator(context)
        }
    }

    private fun createEvaluator(context: Instance): MetaStaticExpressionEvaluator {
        val evaluator = metaStaticExpressionEvaluatorProvider.get()
        evaluator.instance = context
        return evaluator
    }

    fun evaluate(context: Instance, expression: Expression): NamedElement {
        return getEvaluator(context).evaluate(expression)
    }

}
