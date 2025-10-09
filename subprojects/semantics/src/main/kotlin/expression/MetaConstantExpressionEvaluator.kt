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

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTypedOrNull(clazz: Class<T>, expression: Expression): T? {
    return evaluate(expression) as? T
}

inline fun <reified T : A, A> ExpressionEvaluator<A>.evaluateTyped(clazz: Class<T>, expression: Expression): T {
    return evaluateTypedOrNull(clazz, expression) ?: error("Expression does not evaluate to type ${T::class.qualifiedName}")
}

open class MetaConstantExpressionEvaluator : ExpressionEvaluator<NamedElement>() {

    override fun visit(expression: RangeExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: ComparisonOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: ArithmeticBinaryOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: BooleanOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: ArithmeticUnaryOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: NegationOperator): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: ArrayLiteral): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralInfinity): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralReal): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralInteger): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralString): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralBoolean): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: LiteralNothing): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: ElementReference): NamedElement {
        return expression.element
    }

    override fun visit(expression: SelfReference): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: NavigationSuffixExpression): NamedElement {
        error("This expression is a static meta-expression!")
    }

    override fun visit(expression: CallSuffixExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

    override fun visit(expression: IndexingSuffixExpression): NamedElement {
        error("This expression is not a meta-expression!")
    }

}
