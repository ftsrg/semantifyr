/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public abstract class ExpressionVisitor<T> {

    protected T visit(Expression expression) {
        return switch (expression) {
            case OperatorExpression operatorExpression -> visit(operatorExpression);
            case LiteralExpression literalExpression -> visit(literalExpression);
            case ReferenceExpression referenceExpression -> visit(referenceExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T visit(OperatorExpression expression) {
        return switch (expression) {
            case BinaryOperator binaryOperator -> visit(binaryOperator);
            case UnaryOperator unaryOperator -> visit(unaryOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T visit(BinaryOperator expression) {
        return switch (expression) {
            case RangeExpression rangeExpression -> visit(rangeExpression);
            case ComparisonOperator comparisonOperator -> visit(comparisonOperator);
            case ArithmeticBinaryOperator arithmeticBinaryOperator -> visit(arithmeticBinaryOperator);
            case BooleanOperator booleanOperator -> visit(booleanOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(RangeExpression expression);
    protected abstract T visit(ComparisonOperator expression);
    protected abstract T visit(ArithmeticBinaryOperator expression);
    protected abstract T visit(BooleanOperator expression);

    protected T visit(UnaryOperator expression) {
        return switch (expression) {
            case ArithmeticUnaryOperator arithmeticUnaryOperator -> visit(arithmeticUnaryOperator);
            case NegationOperator negationOperator -> visit(negationOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ArithmeticUnaryOperator expression);
    protected abstract T visit(NegationOperator expression);

    protected T visit(LiteralExpression expression) {
        return switch (expression) {
            case ArrayLiteral literalInfinity -> visit(literalInfinity);
            case LiteralInfinity literalInfinity -> visit(literalInfinity);
            case LiteralReal literalReal -> visit(literalReal);
            case LiteralInteger literalInteger -> visit(literalInteger);
            case LiteralString literalString -> visit(literalString);
            case LiteralBoolean literalBoolean -> visit(literalBoolean);
            case LiteralNothing literalNothing -> visit(literalNothing);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ArrayLiteral expression);
    protected abstract T visit(LiteralInfinity expression);
    protected abstract T visit(LiteralReal expression);
    protected abstract T visit(LiteralInteger expression);
    protected abstract T visit(LiteralString expression);
    protected abstract T visit(LiteralBoolean expression);
    protected abstract T visit(LiteralNothing expression);

    protected T visit(ReferenceExpression expression) {
        return switch (expression) {
            case DirectReferenceExpression directReferenceExpression -> visit(directReferenceExpression);
            case PostfixUnaryExpression postfixUnaryExpression -> visit(postfixUnaryExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T visit(DirectReferenceExpression expression) {
        return switch (expression) {
            case ElementReference elementReference -> visit(elementReference);
            case SelfReference selfReference -> visit(selfReference);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ElementReference expression);
    protected abstract T visit(SelfReference expression);

    protected T visit(PostfixUnaryExpression expression) {
        return switch (expression) {
            case NavigationSuffixExpression navigationSuffixExpression -> visit(navigationSuffixExpression);
            case CallSuffixExpression callSuffixExpression -> visit(callSuffixExpression);
            case IndexingSuffixExpression indexingSuffixExpression -> visit(indexingSuffixExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(NavigationSuffixExpression expression);
    protected abstract T visit(CallSuffixExpression expression);
    protected abstract T visit(IndexingSuffixExpression expression);

}
