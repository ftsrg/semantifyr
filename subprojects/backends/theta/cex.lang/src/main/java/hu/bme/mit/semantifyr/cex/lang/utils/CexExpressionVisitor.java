/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cex.lang.utils;

import hu.bme.mit.semantifyr.cex.lang.cex.*;

public abstract class CexExpressionVisitor<T> {

    protected T visit(Expression expression) {
        return switch (expression) {
            case ArithmeticUnaryOperator arithmeticUnaryOperator -> visit(arithmeticUnaryOperator);
            case LiteralExpression literalExpression -> visit(literalExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(ArithmeticUnaryOperator expression);

    protected T visit(LiteralExpression expression) {
        return switch (expression) {
            case LiteralInteger literalInteger -> visit(literalInteger);
            case LiteralBoolean literalBoolean -> visit(literalBoolean);
            case LiteralEnum literalEnum -> visit(literalEnum);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    protected abstract T visit(LiteralInteger expression);
    protected abstract T visit(LiteralBoolean expression);
    protected abstract T visit(LiteralEnum expression);

}
