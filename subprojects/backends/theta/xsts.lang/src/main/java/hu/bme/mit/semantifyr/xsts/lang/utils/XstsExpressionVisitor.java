/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.utils;

import hu.bme.mit.semantifyr.xsts.lang.xsts.*;

public abstract class XstsExpressionVisitor<T> {

    protected T visit(Expression expression) {
        return switch (expression) {
            case ArithmeticBinaryOperator arithmeticBinaryOperator -> visit(arithmeticBinaryOperator);
            case ArithmeticUnaryOperator arithmeticUnaryOperator -> visit(arithmeticUnaryOperator);
            case BooleanOperator booleanOperator -> visit(booleanOperator);
            case ComparisonOperator comparisonOperator -> visit(comparisonOperator);
            case ElementReferenceExpression elementReferenceExpression -> visit(elementReferenceExpression);
            case LiteralExpression literalExpression -> visit(literalExpression);
            case IfThenElseExpression ifThenElseExpression -> visit(ifThenElseExpression);
            case NegationOperator negationOperator -> visit(negationOperator);
            case ReadIndexingSuffixExpression readIndexingSuffixExpression -> visit(readIndexingSuffixExpression);
            case WriteIndexingSuffixExpression writeIndexingSuffixExpression -> visit(writeIndexingSuffixExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ArithmeticBinaryOperator expression);
    protected abstract T visit(ArithmeticUnaryOperator expression);
    protected abstract T visit(BooleanOperator expression);
    protected abstract T visit(ComparisonOperator expression);
    protected abstract T visit(ElementReferenceExpression expression);
    protected abstract T visit(IfThenElseExpression expression);
    protected abstract T visit(NegationOperator expression);
    protected abstract T visit(ReadIndexingSuffixExpression expression);
    protected abstract T visit(WriteIndexingSuffixExpression expression);

    protected T visit(LiteralExpression expression) {
        return switch (expression) {
            case LiteralArray literalArray -> visit(literalArray);
            case LiteralBoolean literalBoolean -> visit(literalBoolean);
            case LiteralInteger literalInteger -> visit(literalInteger);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(LiteralBoolean expression);
    protected abstract T visit(LiteralInteger expression);

    protected T visit(LiteralArray expression) {
        return switch (expression) {
            case ConcreteLiteralArray concreteLiteralArray -> visit(concreteLiteralArray);
            case DefaultLiteralArray defaultLiteralArray -> visit(defaultLiteralArray);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ConcreteLiteralArray expression);
    protected abstract T visit(DefaultLiteralArray expression);

}
