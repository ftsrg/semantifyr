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

    override fun visit(expression: ElementReference): NamedElement {
        return redefinitionAwareReferenceResolver.resolve(instance, expression.element)
    }

    override fun visit(expression: NavigationSuffixExpression): NamedElement {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val context = evaluator.evaluateSingleInstanceOrNull(expression.primary)

        if (context == null) {
            // Found no instance, so we must assume this member will not be used
            // If used, it will throw an exception in the non-meta evaluator anyway
            // TODO: log this!
            return expression.member
        }

        return redefinitionAwareReferenceResolver.resolve(context, expression.member)
    }

}
