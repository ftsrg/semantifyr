/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.clock;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;

public class ClockTestAnalysis {

    private static final Object SELF = new Object();

    @Inject
    private BuiltinSymbolResolver builtinSymbolResolver;

    public boolean isClock(VariableDeclaration variableDeclaration) {
        var typeSpecification = variableDeclaration.getTypeSpecification();
        return typeSpecification != null
                && typeSpecification.getDomain() != null
                && typeSpecification.getDomain() == builtinSymbolResolver.clockDatatype(variableDeclaration);
    }

    public VariableDeclaration referencedClock(Expression expression) {
        EObject element = switch (expression) {
            case ElementReference reference -> reference.getElement();
            case NavigationSuffixExpression navigation -> navigation.getMember();
            default -> null;
        };
        if (element instanceof VariableDeclaration variable && !variable.eIsProxy() && isClock(variable)) {
            return variable;
        }
        return null;
    }

    public boolean isClockTest(ComparisonOperator comparison) {
        return isClockValued(comparison.getLeft()) || isClockValued(comparison.getRight());
    }

    public boolean isClockValued(Expression expression) {
        if (expression instanceof IfThenElse ifThenElse) {
            return isClockValued(ifThenElse.getThen()) || isClockValued(ifThenElse.getElse());
        }
        return expression != null && referencedClock(expression) != null;
    }

    public List<ComparisonOperator> unclosedTests(Operation body) {
        var unclosed = new ArrayList<ComparisonOperator>();
        visit(body, new HashSet<>(), unclosed);
        return unclosed;
    }

    private Set<List<Object>> visit(Operation operation, Set<List<Object>> reset, List<ComparisonOperator> unclosed) {
        switch (operation) {
            case SequenceOperation sequence -> {
                var current = reset;
                for (var step : sequence.getSteps()) {
                    current = visit(step, current, unclosed);
                }
                return current;
            }
            case ChoiceOperation choice -> {
                Set<List<Object>> common = null;
                for (var branch : choice.getBranches()) {
                    common = intersect(common, visit(branch, new HashSet<>(reset), unclosed));
                }
                return common == null ? reset : common;
            }
            case IfOperation ifOperation -> {
                collect(ifOperation.getGuard(), null, false, reset, unclosed);
                return visitBranches(ifOperation.getBody(), ifOperation.getElse(), reset, unclosed);
            }
            case InlineIfOperation inlineIf -> {
                collect(inlineIf.getGuard(), null, false, reset, unclosed);
                return visitBranches(inlineIf.getBody(), inlineIf.getElse(), reset, unclosed);
            }
            case AbstractForOperation forOperation -> {
                collect(forOperation.getRangeExpression(), null, false, reset, unclosed);
                visit(forOperation.getBody(), new HashSet<>(reset), unclosed);
                if (forOperation instanceof InlineForOperation inlineFor && inlineFor.getElse() != null) {
                    visit(inlineFor.getElse(), new HashSet<>(reset), unclosed);
                }
                return reset;
            }
            case AssumptionOperation assumption -> {
                collect(assumption.getExpression(), true, true, reset, unclosed);
                return reset;
            }
            case AssignmentOperation assignment -> {
                collect(assignment.getExpression(), null, false, reset, unclosed);
                var clock = referencedClock(assignment.getReference());
                var key = clock == null ? null : keyOf(assignment.getReference());
                if (key == null) {
                    return reset;
                }
                var updated = new HashSet<>(reset);
                updated.add(key);
                return updated;
            }
            case LocalVarDeclarationOperation localVariable -> {
                collect(localVariable.getExpression(), null, false, reset, unclosed);
                return reset;
            }
            case InlineCall inlineCall -> {
                collect(inlineCall.getCallExpression(), null, false, reset, unclosed);
                return reset;
            }
            default -> {
                return reset;
            }
        }
    }

    private Set<List<Object>> visitBranches(
            SequenceOperation body,
            SequenceOperation elseBody,
            Set<List<Object>> reset,
            List<ComparisonOperator> unclosed) {
        var thenReset = visit(body, new HashSet<>(reset), unclosed);
        var elseReset = elseBody == null ? reset : visit(elseBody, new HashSet<>(reset), unclosed);
        return intersect(thenReset, elseReset);
    }

    private static Set<List<Object>> intersect(Set<List<Object>> left, Set<List<Object>> right) {
        if (left == null) {
            return right;
        }
        var result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private void collect(
            Expression expression,
            Boolean polarity,
            boolean inAssumption,
            Set<List<Object>> reset,
            List<ComparisonOperator> unclosed) {
        if (expression == null) {
            return;
        }
        switch (expression) {
            case ComparisonOperator comparison
            when isClockTest(comparison) -> {
                if (!isClosed(comparison, polarity, inAssumption) && !isReset(comparison, reset)) {
                    unclosed.add(comparison);
                }
            }
            case NegationOperator negation ->
                collect(negation.getBody(), polarity == null ? null : !polarity, inAssumption, reset, unclosed);
            case BooleanOperator operator
            when operator.getOp() != BooleanOp.XOR -> {
                collect(operator.getLeft(), polarity, inAssumption, reset, unclosed);
                collect(operator.getRight(), polarity, inAssumption, reset, unclosed);
            }
            case IfThenElse ifThenElse -> {
                collect(ifThenElse.getGuard(), null, inAssumption, reset, unclosed);
                collect(ifThenElse.getThen(), polarity, inAssumption, reset, unclosed);
                collect(ifThenElse.getElse(), polarity, inAssumption, reset, unclosed);
            }
            default -> {
                for (var child : expression.eContents()) {
                    if (child instanceof Expression childExpression) {
                        collect(childExpression, null, inAssumption, reset, unclosed);
                    } else {
                        for (var nested : child.eContents()) {
                            if (nested instanceof Expression nestedExpression) {
                                collect(nestedExpression, null, inAssumption, reset, unclosed);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isClosed(ComparisonOperator comparison, Boolean polarity, boolean inAssumption) {
        if (!inAssumption || polarity == null) {
            return false;
        }
        if (clockLeaves(comparison.getLeft()) == null && clockLeaves(comparison.getRight()) == null) {
            return false;
        }
        var op = polarity ? comparison.getOp() : negated(comparison.getOp());
        return op == ComparisonOp.LESS_EQ || op == ComparisonOp.GREATER_EQ || op == ComparisonOp.EQ;
    }

    private static ComparisonOp negated(ComparisonOp op) {
        return switch (op) {
            case LESS -> ComparisonOp.GREATER_EQ;
            case LESS_EQ -> ComparisonOp.GREATER;
            case GREATER -> ComparisonOp.LESS_EQ;
            case GREATER_EQ -> ComparisonOp.LESS;
            case EQ -> ComparisonOp.NOT_EQ;
            case NOT_EQ -> ComparisonOp.EQ;
        };
    }

    private boolean isReset(ComparisonOperator comparison, Set<List<Object>> reset) {
        var leaves = clockLeaves(comparison.getLeft());
        if (leaves == null) {
            leaves = clockLeaves(comparison.getRight());
        }
        if (leaves == null) {
            return false;
        }
        for (var leaf : leaves) {
            var key = keyOf(leaf);
            if (key == null || !reset.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private List<Expression> clockLeaves(Expression expression) {
        if (expression instanceof IfThenElse ifThenElse) {
            var thenLeaves = clockLeaves(ifThenElse.getThen());
            var elseLeaves = clockLeaves(ifThenElse.getElse());
            if (thenLeaves == null || elseLeaves == null) {
                return null;
            }
            var leaves = new ArrayList<>(thenLeaves);
            leaves.addAll(elseLeaves);
            return leaves;
        }
        if (expression != null && referencedClock(expression) != null) {
            return List.of(expression);
        }
        return null;
    }

    private static List<Object> keyOf(Expression expression) {
        return switch (expression) {
            case ElementReference reference -> new ArrayList<>(List.of(reference.getElement()));
            case SelfReference ignored -> new ArrayList<>(List.of(SELF));
            case NavigationSuffixExpression navigation -> {
                var primary = keyOf(navigation.getPrimary());
                if (primary == null) {
                    yield null;
                }
                primary.add(navigation.getMember());
                yield primary;
            }
            default -> null;
        };
    }
}
