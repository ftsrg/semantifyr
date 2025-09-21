/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import kotlin.reflect.KClass

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTypedOrNull(clazz: Class<T>, expression: Expression): T? {
    return evaluate(expression) as? T
}

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTyped(clazz: Class<T>, expression: Expression): T {
    return evaluateTypedOrNull(clazz, expression) ?: error("Expression does not evaluate to type ${T::class.qualifiedName}")
}

open class MetaConstantExpressionEvaluator : ExpressionEvaluator<NamedElement>() {

    override fun compute(expression: RangeExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: ComparisonOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: ArithmeticBinaryOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: BooleanOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: ArithmeticUnaryOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: NegationOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: ArrayLiteral): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralInfinity): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralReal): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralInteger): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralString): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralBoolean): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: LiteralNothing): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: ElementReference): NamedElement {
        return expression.element
    }

    override fun compute(expression: SelfReference): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: NavigationSuffixExpression): NamedElement {
        error("This expression is a static meta-expression!")
    }

    override fun compute(expression: CallSuffixExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun compute(expression: IndexingSuffixExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

}
