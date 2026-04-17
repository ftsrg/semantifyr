/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped

@CompilationScoped
class MetaStaticExpressionEvaluatorProvider @Inject constructor(
    private val metaStaticExpressionEvaluatorFactory: MetaStaticExpressionEvaluator.Factory,
) {

    private val cache = mutableMapOf<Instance, MetaStaticExpressionEvaluator>()

    fun getEvaluator(context: Instance): MetaStaticExpressionEvaluator {
        return cache.getOrPut(context) {
            metaStaticExpressionEvaluatorFactory.create(context)
        }
    }

    fun evaluate(context: Instance, expression: Expression): NamedElement {
        return getEvaluator(context).evaluate(expression)
    }

}
