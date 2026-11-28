/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.modality;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

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
    protected Modality visit(RecordLiteral expression) {
        Modality result = Modality.CONSTANT;
        for (FieldAssignment field : expression.getFields()) {
            if (field.getExpression() != null) {
                result = result.leastUpperBound(evaluate(field.getExpression()));
            }
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
        var result = evaluate(expression.getPrimary());
        for (Argument arg : expression.getArguments()) {
            if (arg.getExpression() != null) {
                result = result.leastUpperBound(evaluate(arg.getExpression()));
            }
        }
        return result;
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
            case EnumDeclaration ignored -> Modality.CONSTANT;
            case DataTypeDeclaration ignored -> Modality.CONSTANT;
            case FeatureDeclaration ignored -> Modality.COMPILE_TIME;
            case ClassDeclaration ignored -> Modality.COMPILE_TIME;
            case RecordDeclaration ignored -> Modality.COMPILE_TIME;
            // FIXME: this is not always the case!
            case ParameterDeclaration ignored -> Modality.COMPILE_TIME;
            // `inline for` substitutes its loop variable before the body is evaluated.
            case VariableDeclaration variable
            when variable.eContainer() instanceof InlineForOperation forOperation
                    && forOperation.getLoopVariable() == variable -> Modality.COMPILE_TIME;
            case VariableDeclaration ignored -> Modality.RUNTIME;
            case TransitionDeclaration ignored -> Modality.RUNTIME;
            case PropertyDeclaration ignored -> Modality.RUNTIME;
            default -> Modality.RUNTIME;
        };
    }
}
