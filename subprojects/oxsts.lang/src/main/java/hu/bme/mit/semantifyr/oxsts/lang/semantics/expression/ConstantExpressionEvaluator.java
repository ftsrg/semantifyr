/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;

public class ConstantExpressionEvaluator extends ExpressionEvaluator<ExpressionEvaluation> {

    @Inject
    protected ConstantElementValueEvaluatorProvider elementValueEvaluatorProvider;

    protected ElementValueEvaluator<ExpressionEvaluation> getElementValueEvaluator(EObject context) {
        return elementValueEvaluatorProvider.getEvaluator(context);
    }

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
                default -> throw EvaluationFailureException.at(expression, "Boolean expression can only be == or !=!");
            };
        }

        if (left instanceof EnumLiteralEvaluation(var lValue) && right instanceof EnumLiteralEvaluation(var rValue)) {
            return switch (expression.getOp()) {
                case EQ -> new BooleanEvaluation(lValue == rValue);
                case NOT_EQ -> new BooleanEvaluation(lValue != rValue);
                default -> throw EvaluationFailureException.at(expression, "Boolean expression can only be == or !=!");
            };
        }

        throw EvaluationFailureException.at(expression, "Left and right are not supported expressions!");
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
                case DIV -> {
                    if (rValue == 0) {
                        throw EvaluationFailureException.at(expression, "Division by zero!");
                    }
                    yield new IntegerEvaluation(lValue / rValue);
                }
            };
        }

        throw EvaluationFailureException.at(expression, "Left and right are not integer expressions!");
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

        throw EvaluationFailureException.at(expression, "Left and right are not boolean expressions!");
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

        throw EvaluationFailureException.at(expression, "Body is not an integer or real expression!");
    }

    @Override
    protected ExpressionEvaluation visit(NegationOperator expression) {
        var evaluation = evaluate(expression.getBody());

        if (evaluation instanceof BooleanEvaluation(boolean value)) {
            return new BooleanEvaluation(!value);
        }

        throw EvaluationFailureException.at(expression, "Expression body is not a boolean expression!");
    }

    @Override
    protected ExpressionEvaluation visit(AG expression) {
        throw EvaluationFailureException.at(expression, "AG expressions are not constant evaluable!");
    }

    @Override
    protected ExpressionEvaluation visit(EF expression) {
        throw EvaluationFailureException.at(expression, "EF expressions are not constant evaluable!");
    }

    @Override
    protected ExpressionEvaluation visit(ArrayLiteral expression) {
        return new ArrayEvaluation(
                expression.getValues().stream().map(this::evaluate).toList());
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
        return NothingEvaluation.INSTANCE;
    }

    @Override
    protected ExpressionEvaluation visit(ElementReference expression) {
        return evaluateElement(expression.getElement());
    }

    public ExpressionEvaluation evaluateElement(NamedElement element) {
        if (element.eIsProxy()) {
            throw new IllegalStateException(
                    SourceLocation.prefixFor(element) + "Element could not be resolved (unresolved proxy).");
        }

        return getElementValueEvaluator(element).evaluate(element);
    }

    @Override
    protected ExpressionEvaluation visit(SelfReference expression) {
        throw EvaluationFailureException.at(expression, "This evaluator can only evaluate constant expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(NavigationSuffixExpression expression) {
        throw EvaluationFailureException.at(expression, "This evaluator can only evaluate constant expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(CallSuffixExpression expression) {
        // TODO: implement with global functions
        throw EvaluationFailureException.at(expression, "This evaluator can only evaluate constant expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(IndexingSuffixExpression expression) {
        throw EvaluationFailureException.at(expression, "This evaluator can only evaluate constant expressions!");
    }

    @Override
    protected ExpressionEvaluation visit(CastExpression expression) {
        // A cast preserves the runtime value; only the static type changes.
        // The validator catches incompatible casts ahead of time.
        return evaluate(expression.getBody());
    }

    @Override
    protected ExpressionEvaluation visit(IfThenElse expression) {
        var guard = evaluate(expression.getGuard());
        if (guard instanceof BooleanEvaluation(var value)) {
            return value ? evaluate(expression.getThen()) : evaluate(expression.getElse());
        }

        throw EvaluationFailureException.at(expression, "if-then-else guard did not evaluate to a boolean: " + guard);
    }
}
