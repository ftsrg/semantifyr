/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import org.eclipse.xtext.util.Tuples

@Singleton
class MetaStaticExpressionEvaluatorProvider {

    private val CACHE_KEY: String = "${javaClass.canonicalName}.CACHE_KEY"

    @Inject
    private lateinit var resourceScopeCache: OnResourceSetChangeEvictingCache

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: Provider<MetaStaticExpressionEvaluator>

    fun getEvaluator(context: Instance): MetaStaticExpressionEvaluator {
        return resourceScopeCache.get(Tuples.create(CACHE_KEY, context), context.eResource()) {
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
