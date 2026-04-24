/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTypedOrNull(clazz: Class<T>, expression: Expression): T? {
    return evaluate(expression) as? T
}

inline fun <reified T : A, A> ExpressionEvaluator<A>.tryEvaluateTypedOrNull(clazz: Class<T>, expression: Expression): T? {
    return try {
        evaluate(expression) as? T
    } catch (_: EvaluationFailureException) {
        null
    }
}

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTyped(clazz: Class<T>, expression: Expression): T {
    return evaluateTypedOrNull(clazz, expression) ?: sourceError(expression, "Expression does not evaluate to type ${T::class.qualifiedName}")
}

class MetaCompileTimeExpressionEvaluator @AssistedInject constructor(
    @param:Assisted val instance: Instance,
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
) : MetaConstantExpressionEvaluator() {

    private val logger by loggerFactory()

    override fun visit(expression: ElementReference): NamedElement {
        if (expression.element.eIsProxy()) {
            sourceError(expression, "Element reference could not be resolved (unresolved proxy)")
        }

        return redefinitionAwareReferenceResolver.resolve(instance.domain, expression.element)
    }

    override fun visit(expression: NavigationSuffixExpression): NamedElement {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val context = evaluator.evaluateSingleInstanceOrNull(expression.primary)

        if (context == null) {
            // Found no instance, so we must assume this member will not be used
            // If used, it will throw an exception in the non-meta evaluator anyway
            logger.debug { "Meta-evaluator: navigation '${expression.member.name}' has no concrete context at instance '${instance.name}'. Returning grammar-level member for meta navigation." }
            return expression.member
        }

        return redefinitionAwareReferenceResolver.resolve(context.domain, expression.member)
    }

    interface Factory {
        fun create(instance: Instance): MetaCompileTimeExpressionEvaluator
    }

}
