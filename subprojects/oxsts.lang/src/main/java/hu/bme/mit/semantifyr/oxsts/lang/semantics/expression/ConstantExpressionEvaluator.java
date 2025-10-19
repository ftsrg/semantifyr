/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public class ConstantExpressionEvaluator extends ExpressionEvaluator<ExpressionEvaluation> {

    @Override
    protected ExpressionEvaluation visit(RangeExpression expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        return RangeEvaluation.of(left, right);
    }

    @Override
    protected ExpressionEvaluation visit(ComparisonOperator expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        if (left instanceof IntegerEvaluation(var lValue) && right instanceof IntegerEvaluation(var rValue)) {
            return switch (expression.getOp()) {
                case LESS -> new BooleanEvaluation(lValue < rValue);
                case LESS_EQ -> new BooleanEvaluation(lValue <= rValue);
                case GREATER -> new BooleanEvaluation(lValue > rValue);
                case GREATER_EQ -> new BooleanEvaluation(lValue >= rValue);
                case EQ -> new BooleanEvaluation(lValue == rValue);
                case NOT_EQ -> new BooleanEvaluation(lValue != rValue);
            };
        }

        if (left instanceof BooleanEvaluation(var lValue) && right instanceof BooleanEvaluation(var rValue)) {
            return switch (expression.getOp()) {
                case EQ -> new BooleanEvaluation(lValue == rValue);
                case NOT_EQ -> new BooleanEvaluation(lValue != rValue);
                default -> throw  new IllegalArgumentException("Boolean expression can only be == or !=!");
            };
        }

        if (left instanceof EnumLiteralEvaluation(var lValue) && right instanceof EnumLiteralEvaluation(var rValue)) {
            return switch (expression.getOp()) {
                case EQ -> new BooleanEvaluation(lValue == rValue);
                case NOT_EQ -> new BooleanEvaluation(lValue != rValue);
                default -> throw  new IllegalArgumentException("Boolean expression can only be == or !=!");
            };
        }

        throw new IllegalArgumentException("Left and right are not supported expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(ArithmeticBinaryOperator expression) {
        // FIXME: real evaluations, auto casting, etc.
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        if (left instanceof IntegerEvaluation(int lValue) && right instanceof IntegerEvaluation(int rValue)) {
            return switch (expression.getOp()) {
                case ADD -> new IntegerEvaluation(lValue + rValue);
                case SUB -> new IntegerEvaluation(lValue - rValue);
                case MUL -> new IntegerEvaluation(lValue * rValue);
                case DIV -> new IntegerEvaluation(lValue / rValue);
            };
        }

        throw new IllegalArgumentException("Left and right are not integer expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(BooleanOperator expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());

        if (left instanceof BooleanEvaluation(boolean lValue) && right instanceof BooleanEvaluation(boolean rValue)) {
            return switch (expression.getOp()) {
                case OR -> new BooleanEvaluation(lValue || rValue);
                case AND -> new BooleanEvaluation(lValue && rValue);
                case XOR -> new BooleanEvaluation(lValue ^ rValue);
            };
        }

        throw new IllegalArgumentException("Left and right are not boolean expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(ArithmeticUnaryOperator expression) {
        var body = evaluate(expression.getBody());

        if (body instanceof IntegerEvaluation(int value)) {
            return switch (expression.getOp()) {
                case MINUS -> new IntegerEvaluation(-value);
                case PLUS -> body;
            };
        }

        if (body instanceof RealEvaluation(double value)) {
            return switch (expression.getOp()) {
                case MINUS -> new RealEvaluation(-value);
                case PLUS -> body;
            };
        }

        throw new IllegalArgumentException("Body is not an integer or real expression!");
    }

    @Override
    protected ExpressionEvaluation visit(NegationOperator expression) {
        var evaluation = evaluate(expression.getBody());

        if (evaluation instanceof BooleanEvaluation(boolean value)) {
            return new BooleanEvaluation(!value);
        }

        throw new IllegalArgumentException("Expression body is not a boolean expression!");
    }

    @Override
    protected ExpressionEvaluation visit(ArrayLiteral expression) {
        return new ArrayEvaluation(expression.getValues().stream().map(this::evaluate).toList());
    }

    @Override
    protected ExpressionEvaluation visit(LiteralInfinity expression) {
        return InfinityEvaluation.INSTANCE;
    }

    @Override
    protected ExpressionEvaluation visit(LiteralReal expression) {
        return new RealEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation visit(LiteralInteger expression) {
        return new IntegerEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation visit(LiteralString expression) {
        return new StringEvaluation(expression.getValue());
    }

    @Override
    protected ExpressionEvaluation visit(LiteralBoolean expression) {
        return new BooleanEvaluation(expression.isValue());
    }

    @Override
    protected ExpressionEvaluation visit(LiteralNothing expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(ElementReference expression) {
        return evaluateElement(expression.getElement());
    }

    protected ExpressionEvaluation evaluateElement(NamedElement element) {
        //noinspection SwitchStatementWithTooFewBranches - expected to be extended with additional types
        return switch (element) {
            case EnumLiteral enumLiteral -> new EnumLiteralEvaluation(enumLiteral);
            default -> throw new IllegalArgumentException("Unsupported type of referenced element!");
        };
    }

    @Override
    protected ExpressionEvaluation visit(SelfReference expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(NavigationSuffixExpression expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(CallSuffixExpression expression) {
        // TODO: implement with global functions
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(IndexingSuffixExpression expression) {
        throw new IllegalArgumentException("This evaluator can only evaluate non-contextual expressions!");
    }

}
