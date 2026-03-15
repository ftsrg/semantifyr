/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.serializer;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

public class ExpressionSerializer extends ExpressionVisitor<String> {

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    public String serialize(Expression expression) {
        return visit(expression);
    }

    @Override
    protected String visit(RangeExpression expression) {
        var operator = expression.isExclusive() ? "..<" : "..";

        return visit(expression.getLeft()) + operator + visit(expression.getRight());
    }

    protected String stringifyOperator(ComparisonOp operator) {
        return switch (operator) {
            case LESS -> " < ";
            case LESS_EQ -> " <= ";
            case GREATER -> " > ";
            case GREATER_EQ -> " >= ";
            case EQ -> " == ";
            case NOT_EQ -> " != ";
        };
    }

    @Override
    protected String visit(ComparisonOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }
    protected String stringifyOperator(ArithmeticOp operator) {
        return switch (operator) {
            case ADD -> " + ";
            case SUB -> " - ";
            case MUL -> " * ";
            case DIV -> " / ";
        };
    }

    @Override
    protected String visit(ArithmeticBinaryOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }

    protected String stringifyOperator(BooleanOp operator) {
        return switch (operator) {
            case AND -> " && ";
            case OR -> " || ";
            case XOR -> " ^^ ";
        };
    }

    @Override
    protected String visit(BooleanOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }

    protected String stringifyOperator(UnaryOp operator) {
        return switch (operator) {
            case PLUS -> "";
            case MINUS -> "-";
        };
    }

    @Override
    protected String visit(ArithmeticUnaryOperator expression) {
        return stringifyOperator(expression.getOp()) + visit(expression.getBody());
    }

    @Override
    protected String visit(NegationOperator expression) {
        return "! " + visit(expression.getBody());
    }

    @Override
    protected String visit(AG expression) {
        return "AG " + visit(expression.getBody());
    }

    @Override
    protected String visit(EF expression) {
        return "EF " + visit(expression.getBody());
    }

    @Override
    protected String visit(ArrayLiteral expression) {
        var values = expression.getValues().stream().map(this::visit).toList();

        return "[" + String.join(", ", values) + "]";
    }

    @Override
    protected String visit(LiteralInfinity expression) {
        return "*";
    }

    @Override
    protected String visit(LiteralReal expression) {
        return Double.toString(expression.getValue());
    }

    @Override
    protected String visit(LiteralInteger expression) {
        return Integer.toString(expression.getValue());
    }

    @Override
    protected String visit(LiteralString expression) {
        return expression.getValue();
    }

    @Override
    protected String visit(LiteralBoolean expression) {
        return expression.isValue() ? "true" : "false";
    }

    @Override
    protected String visit(LiteralNothing expression) {
        return "nothing";
    }

    @Override
    protected String visit(ElementReference expression) {
        return oxstsQualifiedNameProvider.getFullyQualifiedNameString(expression.getElement());
    }

    @Override
    protected String visit(SelfReference expression) {
        return "self";
    }

    @Override
    protected String visit(NavigationSuffixExpression expression) {
        return visit(expression.getPrimary()) + "." + NamingUtil.getName(expression.getMember());
    }

    @Override
    protected String visit(CallSuffixExpression expression) {
        var arguments = expression.getArguments().stream().map(e -> visit(e.getExpression())).toList();

        return visit(expression.getPrimary()) + "(" + String.join(", ", arguments) + ")";
    }

    @Override
    protected String visit(IndexingSuffixExpression expression) {
        return visit(expression.getPrimary()) + "[" + visit(expression.getIndex()) + "]";
    }
}
