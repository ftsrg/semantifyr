/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

import java.util.HashMap;
import java.util.Map;

public abstract class ExpressionEvaluator<T> {

    private final Map<Expression, T> evaluations = new HashMap<>();

    public T evaluate(Expression expression) {
        var evaluation = evaluations.get(expression);

        // cannot use computeIfAbsent due to concurrent modification (recursive calL!)
        if (evaluation == null) {
            evaluation = compute(expression);
            evaluations.put(expression, evaluation);
        }

        return evaluation;
    }

    protected T compute(Expression expression) {
        return switch (expression) {
            case OperatorExpression operatorExpression -> compute(operatorExpression);
            case LiteralExpression literalExpression -> compute(literalExpression);
            case ReferenceExpression referenceExpression -> compute(referenceExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T compute(OperatorExpression expression) {
        return switch (expression) {
            case BinaryOperator binaryOperator -> compute(binaryOperator);
            case UnaryOperator unaryOperator -> compute(unaryOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T compute(BinaryOperator expression) {
        return switch (expression) {
            case RangeExpression rangeExpression -> compute(rangeExpression);
            case ComparisonOperator comparisonOperator -> compute(comparisonOperator);
            case ArithmeticBinaryOperator arithmeticBinaryOperator -> compute(arithmeticBinaryOperator);
            case BooleanOperator booleanOperator -> compute(booleanOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T compute(RangeExpression expression);
    protected abstract T compute(ComparisonOperator expression);
    protected abstract T compute(ArithmeticBinaryOperator expression);
    protected abstract T compute(BooleanOperator expression);

    protected T compute(UnaryOperator expression) {
        return switch (expression) {
            case ArithmeticUnaryOperator arithmeticUnaryOperator -> compute(arithmeticUnaryOperator);
            case NegationOperator negationOperator -> compute(negationOperator);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T compute(ArithmeticUnaryOperator expression);
    protected abstract T compute(NegationOperator expression);

    protected T compute(LiteralExpression expression) {
        return switch (expression) {
            case ArrayLiteral literalInfinity -> compute(literalInfinity);
            case LiteralInfinity literalInfinity -> compute(literalInfinity);
            case LiteralReal literalReal -> compute(literalReal);
            case LiteralInteger literalInteger -> compute(literalInteger);
            case LiteralString literalString -> compute(literalString);
            case LiteralBoolean literalBoolean -> compute(literalBoolean);
            case LiteralNothing literalNothing -> compute(literalNothing);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T compute(ArrayLiteral expression);
    protected abstract T compute(LiteralInfinity expression);
    protected abstract T compute(LiteralReal expression);
    protected abstract T compute(LiteralInteger expression);
    protected abstract T compute(LiteralString expression);
    protected abstract T compute(LiteralBoolean expression);
    protected abstract T compute(LiteralNothing expression);

    protected T compute(ReferenceExpression expression) {
        return switch (expression) {
            case DirectReferenceExpression directReferenceExpression -> compute(directReferenceExpression);
            case PostfixUnaryExpression postfixUnaryExpression -> compute(postfixUnaryExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected T compute(DirectReferenceExpression expression) {
        return switch (expression) {
            case ElementReference elementReference -> compute(elementReference);
            case SelfReference selfReference -> compute(selfReference);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T compute(ElementReference expression);
    protected abstract T compute(SelfReference expression);

    protected T compute(PostfixUnaryExpression expression) {
        return switch (expression) {
            case NavigationSuffixExpression navigationSuffixExpression -> compute(navigationSuffixExpression);
            case CallSuffixExpression callSuffixExpression -> compute(callSuffixExpression);
            case IndexingSuffixExpression indexingSuffixExpression -> compute(indexingSuffixExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T compute(NavigationSuffixExpression expression);
    protected abstract T compute(CallSuffixExpression expression);
    protected abstract T compute(IndexingSuffixExpression expression);

}
