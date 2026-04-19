/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement

@Singleton
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
