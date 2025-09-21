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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement

@Singleton
class MetaConstantExpressionEvaluatorProvider {

    private val CACHE_KEY: String = "${javaClass.canonicalName}.CACHE_KEY"

    @Inject
    private lateinit var resourceScopeCache: OnResourceSetChangeEvictingCache

    @Inject
    private lateinit var metaConstantExpressionEvaluatorProvider: Provider<MetaConstantExpressionEvaluator>

    fun getEvaluator(context: Element): MetaConstantExpressionEvaluator {
        return resourceScopeCache.get(CACHE_KEY, context.eResource(), metaConstantExpressionEvaluatorProvider)
    }

    fun evaluate(expression: Expression): NamedElement {
        return getEvaluator(expression).evaluate(expression)
    }

}
