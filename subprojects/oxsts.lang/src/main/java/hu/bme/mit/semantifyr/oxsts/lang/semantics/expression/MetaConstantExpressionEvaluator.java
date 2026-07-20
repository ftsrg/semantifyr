/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public class MetaConstantExpressionEvaluator extends ExpressionEvaluator<NamedElement> {

    private static final String NOT_META = "This expression is not a meta-expression!";

    @Override
    protected NamedElement visit(RangeExpression expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(ComparisonOperator expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(ArithmeticBinaryOperator expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(BooleanOperator expressionBooleanOperator) {
        throw EvaluationFailureException.at(expressionBooleanOperator, NOT_META);
    }

    @Override
    protected NamedElement visit(ArithmeticUnaryOperator expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(NegationOperator expressionNegationOperator) {
        throw EvaluationFailureException.at(expressionNegationOperator, NOT_META);
    }

    @Override
    protected NamedElement visit(AG expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(EF expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(ArrayLiteral expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralInfinity expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralReal expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralInteger expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralString expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralBoolean expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(LiteralNothing expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(ElementReference expression) {
        if (expression.getElement().eIsProxy()) {
            throw new IllegalStateException(SourceLocation.prefixFor(expression)
                    + "Element reference could not be resolved (unresolved proxy).");
        }

        return expression.getElement();
    }

    @Override
    protected NamedElement visit(SelfReference expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(NavigationSuffixExpression expression) {
        if (expression.getMember().eIsProxy()) {
            throw new IllegalStateException(SourceLocation.prefixFor(expression)
                    + "Navigation member could not be resolved (unresolved proxy).");
        }

        return expression.getMember();
    }

    @Override
    protected NamedElement visit(CallSuffixExpression expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(IndexingSuffixExpression expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(CastExpression expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }

    @Override
    protected NamedElement visit(IfThenElse expression) {
        throw EvaluationFailureException.at(expression, NOT_META);
    }
}
