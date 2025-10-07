/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class MetaConstantExpressionEvaluatorProvider {

    private val cache = mutableMapOf<Element, MetaConstantExpressionEvaluator>()

    @Inject
    private lateinit var metaConstantExpressionEvaluatorProvider: Provider<MetaConstantExpressionEvaluator>

    fun getEvaluator(context: Element): MetaConstantExpressionEvaluator {
        return cache.getOrPut(context) {
            metaConstantExpressionEvaluatorProvider.get()
        }
    }

    fun evaluate(expression: Expression): NamedElement {
        return getEvaluator(expression).evaluate(expression)
    }

}
