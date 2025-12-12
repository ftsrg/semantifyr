/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.debugging;

import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;

class ExpressionTypeRenderer extends ExpressionVisitor<String> {

    public static ExpressionTypeRenderer INSTANCE = new ExpressionTypeRenderer();

    private ExpressionTypeRenderer() {

    }

    public String render(Expression expression) {
        return visit(expression);
    }

    @Override
    protected String visit(RangeExpression expression) {
        var operator = expression.isExclusive() ? "..<" : "..";

        return visit(expression.getLeft()) + operator + visit(expression.getRight());
    }

    @Override
    protected String visit(ComparisonOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }

    protected String stringifyOperator(ComparisonOp operator) {
        return switch (operator) {
            case LESS -> "<";
            case LESS_EQ -> "<=";
            case GREATER -> ">";
            case GREATER_EQ -> ">=";
            case EQ -> "==";
            case NOT_EQ -> "!=";
        };
    }

    @Override
    protected String visit(ArithmeticBinaryOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }

    protected String stringifyOperator(ArithmeticOp operator) {
        return switch (operator) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
        };
    }

    @Override
    protected String visit(BooleanOperator expression) {
        return visit(expression.getLeft()) + stringifyOperator(expression.getOp()) + visit(expression.getRight());
    }

    protected String stringifyOperator(BooleanOp operator) {
        return switch (operator) {
            case AND -> "&&";
            case OR -> "||";
            case XOR -> "^^";
        };
    }

    @Override
    protected String visit(ArithmeticUnaryOperator expression) {
        return stringifyOperator(expression.getOp()) + visit(expression.getBody());
    }

    protected String stringifyOperator(UnaryOp operator) {
        return switch (operator) {
            case PLUS -> "+";
            case MINUS -> "-";
        };
    }

    @Override
    protected String visit(NegationOperator expression) {
        return "!" + visit(expression.getBody());
    }

    @Override
    protected String visit(ArrayLiteral expression) {
        var values = expression.getValues().stream().map(this::visit).toList();

        return "[" + String.join(", ", values) + "]";
    }

    @Override
    protected String visit(LiteralInfinity expression) {
        return "infinity";
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
        return TypeRenderer.render(expression.getElement());
    }

    @Override
    protected String visit(SelfReference expression) {
        return "self";
    }

    @Override
    protected String visit(NavigationSuffixExpression expression) {
        return visit(expression.getPrimary()) + "." + TypeRenderer.render(expression.getMember());
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

public class TypeRenderer {

    public static String render(EObject element) {
        if (element instanceof Expression expression) {
            return ExpressionTypeRenderer.INSTANCE.render(expression);
        }
        if (element instanceof NamedElement namedElement) {
            return render(namedElement);
        }
        if (element instanceof Instance instance) {
            return render(instance);
        }

        return element.toString();
    }

    private static String render(NamedElement element) {
        return NamingUtil.getName(element);
    }

    private static String render(Instance element) {
        var list = new ArrayList<DomainDeclaration>();
        list.add(element.getDomain());

        while (element.getParent() != null && element.getParent().getDomain() != null) {
            list.add(element.getParent().getDomain());
            element = element.getParent();
        }

        var names = list.reversed().stream().map(TypeRenderer::render).toList();

        return String.join(".", names);
    }

}
