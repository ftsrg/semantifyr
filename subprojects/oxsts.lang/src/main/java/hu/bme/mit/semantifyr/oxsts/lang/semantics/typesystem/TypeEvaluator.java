/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.EcoreUtil2;

import java.util.HashMap;
import java.util.Map;

public class TypeEvaluator {

    private final Map<Expression, TypeEvaluation> expressionTypes = new HashMap<>();
    private final Map<VariableDeclaration, TypeEvaluation> variableTypes = new HashMap<>();

    public TypeEvaluation evaluateExpressionType(Expression expression) {
        var evaluation = expressionTypes.get(expression);

        // cannot use computeIfAbsent due to concurrent modification (recursive calL!)
        if (evaluation == null) {
            evaluation = computeExpressionType(expression);
            expressionTypes.put(expression, evaluation);
        }

        return evaluation;
    }

    private TypeEvaluation computeExpressionType(Expression expression) {
        return switch (expression) {
            case OperatorExpression operatorExpression -> computeExpressionType(operatorExpression);
            case LiteralExpression literalExpression -> computeExpressionType(literalExpression);
            case ReferenceExpression referenceExpression -> computeExpressionType(referenceExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private TypeEvaluation computeExpressionType(OperatorExpression expression) {
        // TODO

        return TypeEvaluation.INVALID;
    }

    private TypeEvaluation computeExpressionType(LiteralExpression expression) {
        // TODO

        return TypeEvaluation.INVALID;
    }

    private TypeEvaluation computeExpressionType(ReferenceExpression expression) {
        return switch (expression) {
            case ElementReference elementReference -> computeExpressionType(elementReference);
            case SelfReference selfReference -> computeExpressionType(selfReference);
            case PostfixUnaryExpression postfixUnaryExpression -> computeExpressionType(postfixUnaryExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private TypeEvaluation computeExpressionType(ElementReference expression) {
        var referencedElement = expression.getElement();

        // TODO: add validation diagnostic

        return switch (referencedElement) {
            case FeatureDeclaration featureDeclaration -> getTypeOf(featureDeclaration);
            case VariableDeclaration variableDeclaration -> getTypeOf(variableDeclaration);
//            case PropertyDeclaration propertyDeclaration -> getTypeOf(propertyDeclaration);
            default -> throw new IllegalStateException("Unexpected value: " + referencedElement);
        };
    }

    private TypeEvaluation getTypeOf(FeatureDeclaration featureDeclaration) {
        return new ImmutableTypeEvaluation(featureDeclaration);
    }

    private TypeEvaluation getTypeOf(VariableDeclaration variableDeclaration) {
        var evaluation = variableTypes.get(variableDeclaration);

        // cannot use computeIfAbsent due to concurrent modification (recursive calL!)
        if (evaluation == null) {
            evaluation = computeTypeOf(variableDeclaration);
            variableTypes.put(variableDeclaration, evaluation);
        }

        return evaluation;
    }

    private TypeEvaluation computeTypeOf(VariableDeclaration variableDeclaration) {
        var domainDeclaration = variableDeclaration.getType();

        return switch (domainDeclaration) {
//            case FeatureDeclaration featureDeclaration -> new ImmutableTypeEvaluation(featureDeclaration.getType());
            case null -> computeImplicitTypeOf(variableDeclaration);
            default -> new ImmutableTypeEvaluation(domainDeclaration);
        };
    }

    private TypeEvaluation computeImplicitTypeOf(VariableDeclaration variableDeclaration) {
        if (variableDeclaration.getExpression() != null) {
            return evaluateExpressionType(variableDeclaration.getExpression());
        }

        if (variableDeclaration.eContainer() instanceof AbstractForOperation abstractForOperation) {
            return evaluateExpressionType(abstractForOperation.getRangeExpression());
        }

        return TypeEvaluation.INVALID;
    }

    private TypeEvaluation computeExpressionType(SelfReference expression) {
        var classDeclaration = EcoreUtil2.getContainerOfType(expression, ClassDeclaration.class);

        if (classDeclaration == null) {
            // TODO: add validation diagnostic

            return TypeEvaluation.INVALID;
        }

        return new ImmutableTypeEvaluation(classDeclaration);
    }

    private TypeEvaluation computeExpressionType(PostfixUnaryExpression expression) {
        return switch (expression) {
            case NavigationSuffixExpression navigationSuffixExpression -> computeExpressionType(navigationSuffixExpression);
            case CallSuffixExpression callSuffixExpression -> computeExpressionType(callSuffixExpression);
            case IndexingSuffixExpression indexingSuffixExpression -> computeExpressionType(indexingSuffixExpression);
            default -> throw new IllegalStateException("Unexpected value: " + expression);
        };
    }

    private TypeEvaluation computeExpressionType(NavigationSuffixExpression expression) {
        var member = expression.getMember();

        // TODO: add validation diagnostic

        return switch (member) {
            case FeatureDeclaration featureDeclaration -> getTypeOf(featureDeclaration);
//            case DomainDeclaration domainDeclaration -> new ImmutableTypeEvaluation(domainDeclaration);
            case VariableDeclaration variableDeclaration -> getTypeOf(variableDeclaration);
            default -> throw new IllegalStateException("Unexpected value: " + member);
        };
    }

    private TypeEvaluation computeExpressionType(CallSuffixExpression expression) {
        // FIXME: props should also be called, instead of just referenced!
        return TypeEvaluation.INVALID;
    }

    private TypeEvaluation computeExpressionType(IndexingSuffixExpression expression) {
        return evaluateExpressionType(expression.getPrimary());
    }

}
