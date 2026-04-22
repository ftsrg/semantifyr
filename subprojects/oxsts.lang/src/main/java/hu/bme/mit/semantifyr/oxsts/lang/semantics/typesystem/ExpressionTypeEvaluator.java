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
    
    @Inject
    protected TypeCompatibility typeCompatibility;

    @Override
    protected TypeEvaluation visit(RangeExpression expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.intDatatype(expression), RangeEvaluation.SOME);
    }

    @Override
    protected TypeEvaluation visit(ComparisonOperator expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());
        if (left instanceof InvalidTypeEvaluation || right instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArithmeticBinaryOperator expression) {
        var leftType = evaluate(expression.getLeft());
        var rightType = evaluate(expression.getRight());
        if (leftType instanceof InvalidTypeEvaluation || rightType instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }

        var intType = builtinSymbolResolver.intDatatype(expression);
        var realType = builtinSymbolResolver.realDatatype(expression);
        if (leftType.getDomain() == realType || rightType.getDomain() == realType) {
            return new ImmutableTypeEvaluation(realType);
        }
        if (leftType.getDomain() == intType && rightType.getDomain() == intType) {
            return new ImmutableTypeEvaluation(intType);
        }

        // Feature-typed numeric data features (e.g. `refers size: int = 3`)
        // carry their type through the feature declaration. Unwrap one level.
        var leftUnwrapped = typeCompatibility.unwrappedDomain(leftType);
        var rightUnwrapped = typeCompatibility.unwrappedDomain(rightType);
        if (leftUnwrapped == realType || rightUnwrapped == realType) {
            return new ImmutableTypeEvaluation(realType);
        }
        if (leftUnwrapped == intType && rightUnwrapped == intType) {
            return new ImmutableTypeEvaluation(intType);
        }

        return InvalidTypeEvaluation.Instance;
    }

    @Override
    protected TypeEvaluation visit(BooleanOperator expression) {
        var left = evaluate(expression.getLeft());
        var right = evaluate(expression.getRight());
        if (left instanceof InvalidTypeEvaluation || right instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArithmeticUnaryOperator expression) {
        var operandType = evaluate(expression.getBody());
        if (operandType instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }
        if (operandType == null || operandType.getDomain() == null) {
            return InvalidTypeEvaluation.Instance;
        }
        var unwrapped = typeCompatibility.unwrappedDomain(operandType);
        if (unwrapped == builtinSymbolResolver.intDatatype(expression)
            || unwrapped == builtinSymbolResolver.realDatatype(expression)) {
            return new ImmutableTypeEvaluation(unwrapped);
        }
        // Non-numeric operand: inner check reported the root cause.
        return InvalidTypeEvaluation.Instance;
    }

    @Override
    protected TypeEvaluation visit(NegationOperator expression) {
        var operand = evaluate(expression.getBody());
        if (operand instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(AG expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(EF expression) {
        return new ImmutableTypeEvaluation(builtinSymbolResolver.boolDatatype(expression));
    }

    @Override
    protected TypeEvaluation visit(ArrayLiteral expression) {
        // Element type is the (approximate) common type of the values.
        // For an empty literal, fall back to any.
        var values = expression.getValues();
        if (values.isEmpty()) {
            return new ImmutableTypeEvaluation(builtinSymbolResolver.anyDatatype(expression), RangeEvaluation.of(0));
        }
        var elementType = evaluate(values.getFirst());
        var elementDomain = elementType != null ? elementType.getDomain() : builtinSymbolResolver.anyDatatype(expression);
        return new ImmutableTypeEvaluation(elementDomain, RangeEvaluation.of(values.size()));
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
            return InvalidTypeEvaluation.Instance;
        }

        return switch (referencedElement) {
            case VariableDeclaration variableDeclaration -> variableTypeEvaluator.evaluate(variableDeclaration);
            case FeatureDeclaration featureDeclaration -> featureTypeEvaluation(featureDeclaration);
            case ParameterDeclaration parameterDeclaration -> fromTypeSpecification(parameterDeclaration.getTypeSpecification());
            case EnumLiteral enumLiteral -> new ImmutableTypeEvaluation((EnumDeclaration) enumLiteral.eContainer());
            case DomainDeclaration domainDeclaration -> new ImmutableTypeEvaluation(domainDeclaration);
            default -> InvalidTypeEvaluation.Instance;
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
        var primaryType = evaluate(expression.getPrimary());
        if (primaryType == null || primaryType.getDomain() == null) {
            return InvalidTypeEvaluation.Instance;
        }
        return new ImmutableTypeEvaluation(primaryType.getDomain(), RangeEvaluation.ONE);
    }

    @Override
    protected TypeEvaluation visit(CastExpression expression) {
        var typeSpecification = expression.getTypespecification();
        if (typeSpecification == null) {
            return InvalidTypeEvaluation.Instance;
        }
        return fromTypeSpecification(typeSpecification);
    }

    @Override
    protected TypeEvaluation visit(IfThenElse expression) {
        // The result type is the type the two branches have in common. If
        // one is a subtype of the other, that subtype's supertype is taken;
        // for now we approximate with the `then` branch's type and rely on
        // the validator to reject incompatible branches.
        if (expression.getThen() == null || expression.getElse() == null) {
            return InvalidTypeEvaluation.Instance;
        }
        var guard = expression.getGuard() != null ? evaluate(expression.getGuard()) : null;
        var thenType = evaluate(expression.getThen());
        var elseType = evaluate(expression.getElse());
        if (guard instanceof InvalidTypeEvaluation
            || thenType instanceof InvalidTypeEvaluation
            || elseType instanceof InvalidTypeEvaluation) {
            return InvalidTypeEvaluation.Instance;
        }
        return thenType;
    }

    public TypeEvaluation fromTypeSpecification(TypeSpecification typeSpecification) {
        var rangeEvaluation = multiplicityRangeEvaluator.evaluate(typeSpecification);

        return new ImmutableTypeEvaluation(typeSpecification.getDomain(), rangeEvaluation);
    }

    private TypeEvaluation featureTypeEvaluation(FeatureDeclaration featureDeclaration) {
        var typeSpecification = featureDeclaration.getTypeSpecification();
        if (typeSpecification == null) {
            return new ImmutableTypeEvaluation(featureDeclaration);
        }
        var rangeEvaluation = multiplicityRangeEvaluator.evaluate(typeSpecification);
        return new ImmutableTypeEvaluation(featureDeclaration, rangeEvaluation);
    }

}
