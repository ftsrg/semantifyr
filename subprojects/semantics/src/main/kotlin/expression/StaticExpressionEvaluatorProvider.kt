/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import org.eclipse.xtext.util.Tuples

@Singleton
class StaticExpressionEvaluatorProvider {

    private val CACHE_KEY: String = "${javaClass.canonicalName}.CACHE_KEY"

    @Inject
    private lateinit var resourceScopeCache: OnResourceSetChangeEvictingCache

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: Provider<StaticExpressionEvaluator>

    fun getEvaluator(context: Instance): StaticExpressionEvaluator {
        return resourceScopeCache.get(Tuples.create(CACHE_KEY, context), context.eResource()) {
            createEvaluator(context)
        }
    }

    private fun createEvaluator(context: Instance): StaticExpressionEvaluator {
        val evaluator = staticExpressionEvaluatorProvider.get()
        evaluator.instance = context
        return evaluator
    }

    fun evaluate(context: Instance, expression: Expression): ExpressionEvaluation {
        return getEvaluator(context).evaluate(expression)
    }

}
