/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.modality;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CastExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;

public class ExpressionModalityEvaluator extends ExpressionEvaluator<Modality> {

    @Override
    protected Modality visit(LiteralInteger expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(LiteralBoolean expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(LiteralReal expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(LiteralString expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(LiteralNothing expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(LiteralInfinity expression) {
        return Modality.CONSTANT;
    }

    @Override
    protected Modality visit(ArrayLiteral expression) {
        Modality result = Modality.CONSTANT;
        for (Expression value : expression.getValues()) {
            result = result.leastUpperBound(evaluate(value));
        }
        return result;
    }

    @Override
    protected Modality visit(RangeExpression expression) {
        return evaluate(expression.getLeft()).leastUpperBound(evaluate(expression.getRight()));
    }

    @Override
    protected Modality visit(ComparisonOperator expression) {
        return evaluate(expression.getLeft()).leastUpperBound(evaluate(expression.getRight()));
    }

    @Override
    protected Modality visit(ArithmeticBinaryOperator expression) {
        return evaluate(expression.getLeft()).leastUpperBound(evaluate(expression.getRight()));
    }

    @Override
    protected Modality visit(BooleanOperator expression) {
        return evaluate(expression.getLeft()).leastUpperBound(evaluate(expression.getRight()));
    }

    @Override
    protected Modality visit(ArithmeticUnaryOperator expression) {
        return evaluate(expression.getBody());
    }

    @Override
    protected Modality visit(NegationOperator expression) {
        return evaluate(expression.getBody());
    }

    @Override
    protected Modality visit(AG expression) {
        return Modality.RUNTIME;
    }

    @Override
    protected Modality visit(EF expression) {
        return Modality.RUNTIME;
    }

    @Override
    protected Modality visit(SelfReference expression) {
        return Modality.COMPILE_TIME;
    }

    @Override
    protected Modality visit(ElementReference expression) {
        var element = expression.getElement();
        if (element == null || element.eIsProxy()) {
            // Unresolved reference - be conservative.
            return Modality.RUNTIME;
        }
        return modalityOfReference(element);
    }

    @Override
    protected Modality visit(NavigationSuffixExpression expression) {
        var primaryModality = evaluate(expression.getPrimary());
        var member = expression.getMember();
        if (member == null || member.eIsProxy()) {
            return primaryModality.leastUpperBound(Modality.RUNTIME);
        }
        return primaryModality.leastUpperBound(modalityOfReference(member));
    }

    @Override
    protected Modality visit(CallSuffixExpression expression) {
        return Modality.RUNTIME;
    }

    @Override
    protected Modality visit(IndexingSuffixExpression expression) {
        return evaluate(expression.getPrimary()).leastUpperBound(evaluate(expression.getIndex()));
    }

    @Override
    protected Modality visit(CastExpression expression) {
        if (expression.getBody() == null) {
            return Modality.RUNTIME;
        }
        return evaluate(expression.getBody());
    }

    @Override
    protected Modality visit(IfThenElse expression) {
        Modality result = Modality.CONSTANT;
        if (expression.getGuard() != null) {
            result = result.leastUpperBound(evaluate(expression.getGuard()));
        }
        if (expression.getThen() != null) {
            result = result.leastUpperBound(evaluate(expression.getThen()));
        }
        if (expression.getElse() != null) {
            result = result.leastUpperBound(evaluate(expression.getElse()));
        }
        return result;
    }

    private Modality modalityOfReference(NamedElement element) {
        if (element == null) {
            return Modality.RUNTIME;
        }
        return switch (element) {
            case EnumLiteral ignored -> Modality.CONSTANT;
            case FeatureDeclaration ignored -> Modality.COMPILE_TIME;
            case ParameterDeclaration ignored -> Modality.RUNTIME;
            case VariableDeclaration ignored -> Modality.RUNTIME;
            case PropertyDeclaration ignored -> Modality.RUNTIME;
            default -> Modality.RUNTIME;
        };
    }
}
