/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.EcoreUtil2;

public class ExpressionTypeEvaluator extends ExpressionEvaluator<TypeEvaluation> {

    @Inject
    private VariableTypeEvaluator variableTypeEvaluator;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Override
    protected TypeEvaluation visit(RangeExpression expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(ComparisonOperator expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(ArithmeticBinaryOperator expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(BooleanOperator expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(ArithmeticUnaryOperator expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(NegationOperator expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(ArrayLiteral expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(LiteralInfinity expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(LiteralReal expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.realDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(LiteralInteger expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.intDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(LiteralString expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.stringDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(LiteralBoolean expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(LiteralNothing expression) {
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(ElementReference expression) {
        var referencedElement = expression.getElement();

        // TODO: add validation diagnostic
        return switch (referencedElement) {
            case VariableDeclaration variableDeclaration -> variableTypeEvaluator.evaluate(variableDeclaration);
            case DomainDeclaration domainDeclaration -> new ImmutableTypeEvaluation(domainDeclaration);
            case ParameterDeclaration parameterDeclaration -> new ImmutableTypeEvaluation(parameterDeclaration.getType());
            default -> throw new IllegalStateException("Unexpected value: " + referencedElement);
        };
    }

    @Override
    protected TypeEvaluation visit(SelfReference expression) {
        var classDeclaration = expression.getClass_();

        if (classDeclaration == null) {
            classDeclaration = EcoreUtil2.getContainerOfType(expression, ClassDeclaration.class);
        }

        if (classDeclaration == null) {
            // TODO: add validation diagnostic
            return TypeEvaluation.INVALID;
        }

        return new ImmutableTypeEvaluation(classDeclaration);
    }

    @Override
    protected TypeEvaluation visit(NavigationSuffixExpression expression) {
        var member = expression.getMember();

        return switch (member) {
            case VariableDeclaration variableDeclaration -> variableTypeEvaluator.evaluate(variableDeclaration);
            case DomainDeclaration domainDeclaration -> new ImmutableTypeEvaluation(domainDeclaration);
            default -> throw new IllegalStateException("Unexpected value: " + member);
        };
    }

    @Override
    protected TypeEvaluation visit(CallSuffixExpression expression) {
        // TODO: should compute the called type, and then compute its return type
        return TypeEvaluation.INVALID;
    }

    @Override
    protected TypeEvaluation visit(IndexingSuffixExpression expression) {
        return evaluate(expression.getPrimary());
    }

}
