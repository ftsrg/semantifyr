/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.PropertyTypeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.EcoreUtil2;

public class ExpressionTypeEvaluator extends ExpressionEvaluator<TypeEvaluation> {

    @Inject
    private VariableTypeEvaluator variableTypeEvaluator;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    protected PropertyTypeHandler propertyTypeHandler;

    @Inject
    protected MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    @Inject
    protected MultiplicityRangeEvaluator multiplicityRangeEvaluator;

    @Override
    protected TypeEvaluation visit(RangeExpression expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.intDatatype(expression), RangeEvaluation.SOME);
    }

    @Override
    protected TypeEvaluation visit(ComparisonOperator expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArithmeticBinaryOperator expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.anyDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(BooleanOperator expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArithmeticUnaryOperator expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.anyDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(NegationOperator expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArrayLiteral expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.anyDatatype(expression), RangeEvaluation.of(expression.getValues().size()));
    }

    @Override
    protected TypeEvaluation visit(LiteralInfinity expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.anyDatatype(expression));
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
        return NothingTypeEvaluation.Instance;
    }

    @Override
    protected TypeEvaluation visit(ElementReference expression) {
        var referencedElement = expression.getElement();

        if (referencedElement.eIsProxy()) {
            throw new IllegalStateException("Element could not be resolved!");
        }

        // TODO: add validation diagnostic
        return switch (referencedElement) {
            case VariableDeclaration variableDeclaration -> variableTypeEvaluator.evaluate(variableDeclaration);
            case FeatureDeclaration featureDeclaration -> new ImmutableTypeEvaluation(featureDeclaration);
            case ParameterDeclaration parameterDeclaration -> fromTypeSpecification(parameterDeclaration.getTypeSpecification());
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
            return InvalidTypeEvaluation.Instance;
        }

        if (classDeclaration.eIsProxy()) {
            throw new IllegalStateException("Class declaration could not be resolved!");
        }

        return new ImmutableTypeEvaluation(classDeclaration);
    }

    @Override
    protected TypeEvaluation visit(NavigationSuffixExpression expression) {
        var member = expression.getMember();

        if (member.eIsProxy()) {
            throw new IllegalStateException("NavigationSuffix.member could not be resolved!");
        }

        return switch (member) {
            case VariableDeclaration variableDeclaration -> variableTypeEvaluator.evaluate(variableDeclaration);
            case DomainDeclaration domainDeclaration -> new ImmutableTypeEvaluation(domainDeclaration);
            default -> throw new IllegalStateException("Unexpected value: " + member);
        };
    }

    @Override
    protected TypeEvaluation visit(CallSuffixExpression expression) {
        var called = metaConstantExpressionEvaluatorProvider.evaluate(expression.getPrimary());

        return switch (called) {
            case TransitionDeclaration ignored -> InvalidTypeEvaluation.Instance;
            case PropertyDeclaration propertyDeclaration -> fromTypeSpecification(propertyTypeHandler.getPropertyReturnType(propertyDeclaration));
            case RecordDeclaration recordDeclaration -> new ImmutableTypeEvaluation(recordDeclaration);
            default -> throw new IllegalStateException("Unexpected value: " + called);
        };
    }

    @Override
    protected TypeEvaluation visit(IndexingSuffixExpression expression) {
        return evaluate(expression.getPrimary());
    }

    public TypeEvaluation fromTypeSpecification(TypeSpecification typeSpecification) {
        var rangeEvaluation = multiplicityRangeEvaluator.evaluate(typeSpecification);

        return new ImmutableTypeEvaluation(typeSpecification.getDomain(), rangeEvaluation);
    }

}
