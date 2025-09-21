/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression

class MetaStaticExpressionEvaluator : MetaConstantExpressionEvaluator() {

    lateinit var instance: Instance

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    override fun compute(expression: ElementReference): NamedElement {
        return redefinitionAwareReferenceResolver.resolve(instance, expression.element)
    }

    override fun compute(expression: NavigationSuffixExpression): NamedElement {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val context = evaluator.evaluateSingleInstance(expression.primary)

        return redefinitionAwareReferenceResolver.resolve(context, expression.member)
    }

}
