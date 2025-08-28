/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public class ConstantExpressionEvaluator extends ExpressionEvaluator<ExpressionEvaluation> {

    @Override
    protected ExpressionEvaluation compute(RangeExpression expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        return RangeEvaluation.of(left, right);
    }

    @Override
    protected ExpressionEvaluation compute(ComparisonOperator expression) {
        // TODO
        throw new IllegalStateException("Not yet implemented!");
    }

    @Override
    protected ExpressionEvaluation compute(ArithmeticBinaryOperator expression) {
        // FIXME: real evaluations, auto casting, etc.
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        if (left instanceof IntegerEvaluation leftValue && right instanceof IntegerEvaluation rightValue) {
            return switch (expression.getOp()) {
                case ADD -> new IntegerEvaluation(leftValue.value() + rightValue.value());
                case SUB -> new IntegerEvaluation(leftValue.value() - rightValue.value());
                case MUL -> new IntegerEvaluation(leftValue.value() * rightValue.value());
                case DIV -> new IntegerEvaluation(leftValue.value() / rightValue.value());
            };
        }

        throw new IllegalArgumentException("Left and right are not integer expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(BooleanOperator expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        if (left instanceof BooleanEvaluation leftValue && right instanceof BooleanEvaluation rightValue) {
            return switch (expression.getOp()) {
                case OR -> new BooleanEvaluation(leftValue.value() || rightValue.value());
                case AND -> new BooleanEvaluation(leftValue.value() && rightValue.value());
                case XOR -> new BooleanEvaluation(leftValue.value() ^ rightValue.value());
            };
        }

        throw new IllegalArgumentException("Left and right are not boolean expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(ArithmeticUnaryOperator expression) {
        var body = evaluate(expression.getBody());

        if (body instanceof IntegerEvaluation value) {
            return switch (expression.getOp()) {
                case MINUS -> new IntegerEvaluation(- value.value());
                case PLUS -> body;
            };
        }

        if (body instanceof RealEvaluation value) {
            return switch (expression.getOp()) {
                case MINUS -> new RealEvaluation(- value.value());
                case PLUS -> body;
            };
        }

        throw new IllegalArgumentException("Body is not an integer or real expression!");
    }

    @Override
    protected ExpressionEvaluation compute(NegationOperator expression) {
        var evaluation = evaluate(expression.getBody());

        if (evaluation instanceof BooleanEvaluation booleanEvaluation) {
            return new BooleanEvaluation(! booleanEvaluation.value());
        }

        throw new IllegalArgumentException("Expression body is not a boolean expression!");
    }

    @Override
    protected ExpressionEvaluation compute(ArrayLiteral expression) {
        var elements = expression.getValues().stream().map(v -> evaluate(v)).toList();

        return new ArrayEvaluation(elements);
    }

    @Override
    protected ExpressionEvaluation compute(LiteralInfinity expression) {
        return InfinityEvaluation.INSTANCE;
    }

    @Override
    protected ExpressionEvaluation compute(LiteralReal expression) {
        return new RealEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation compute(LiteralInteger expression) {
        return new IntegerEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation compute(LiteralString expression) {
        return new StringEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation compute(LiteralBoolean expression) {
        return new BooleanEvaluation(expression.isValue());
    }

    @Override
    protected ExpressionEvaluation compute(LiteralNothing expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(ElementReference expression) {
        // TODO: implement with global functions
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(SelfReference expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(NavigationSuffixExpression expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(CallSuffixExpression expression) {
        // TODO: implement with global functions
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation compute(IndexingSuffixExpression expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

}
