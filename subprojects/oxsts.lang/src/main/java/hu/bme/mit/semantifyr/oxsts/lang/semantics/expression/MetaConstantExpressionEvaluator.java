/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public class MetaConstantExpressionEvaluator extends ExpressionEvaluator<NamedElement> {

    @Override
    protected NamedElement visit(RangeExpression expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(ComparisonOperator expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(ArithmeticBinaryOperator expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(BooleanOperator expressionBooleanOperator) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(ArithmeticUnaryOperator expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(NegationOperator expressionNegationOperator) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(ArrayLiteral expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralInfinity expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralReal expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralInteger expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralString expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralBoolean expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(LiteralNothing expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(ElementReference expression) {
        if (expression.getElement().eIsProxy()) {
            throw new IllegalStateException("Element could not be resolved!");
        }

        return expression.getElement();
    }

    @Override
    protected NamedElement visit(SelfReference expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(NavigationSuffixExpression expression) {
        if (expression.getMember().eIsProxy()) {
            throw new IllegalStateException("Element could not be resolved!");
        }

        return expression.getMember();
    }

    @Override
    protected NamedElement visit(CallSuffixExpression expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

    @Override
    protected NamedElement visit(IndexingSuffixExpression expression) {
        throw new IllegalStateException("This expression is not a meta-expression!");
    }

}
