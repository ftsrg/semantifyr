/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTypedOrNull(clazz: Class<T>, expression: Expression): T? {
    return evaluate(expression) as? T
}

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTyped(clazz: Class<T>, expression: Expression): T {
    return evaluateTypedOrNull(clazz, expression) ?: error("Expression does not evaluate to type ${T::class.qualifiedName}")
}

class MetaStaticExpressionEvaluator : MetaConstantExpressionEvaluator() {

    lateinit var instance: Instance

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    override fun visit(expression: ElementReference): NamedElement {
        if (expression.element.eIsProxy()) {
            throw IllegalStateException("Element could not be resolved!");
        }

        return redefinitionAwareReferenceResolver.resolve(instance.domain, expression.element)
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

        return redefinitionAwareReferenceResolver.resolve(context.domain, expression.member)
    }

}
