/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.clock;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeCompatibility;
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;

public class ClockTestAnalysis {

    private record ClockPath(boolean fromSelf, List<NamedElement> members) {

        ClockPath extended(NamedElement member) {
            var extendedMembers = new ArrayList<>(members);
            extendedMembers.add(member);
            return new ClockPath(fromSelf, extendedMembers);
        }
    }

    private record ClockSide(List<Expression> leaves, boolean everyBranchIsClock) {}

    @Inject
    private TypeCompatibility typeCompatibility;

    public boolean isClock(VariableDeclaration variableDeclaration) {
        var typeSpecification = variableDeclaration.getTypeSpecification();
        return typeSpecification != null
                && typeCompatibility.isClock(typeSpecification.getDomain(), variableDeclaration);
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

    public ClockTest clockTest(ComparisonOperator comparison) {
        var left = sideOf(comparison.getLeft());
        if (left.everyBranchIsClock()) {
            return new ClockTest(
                    comparison, comparison.getLeft(), left.leaves(), comparison.getRight(), comparison.getOp());
        }
        var right = sideOf(comparison.getRight());
        if (right.everyBranchIsClock()) {
            return new ClockTest(
                    comparison,
                    comparison.getRight(),
                    right.leaves(),
                    comparison.getLeft(),
                    mirrored(comparison.getOp()));
        }
        return null;
    }

    private ClockSide sideOf(Expression expression) {
        if (expression instanceof IfThenElse ifThenElse) {
            var thenSide = sideOf(ifThenElse.getThen());
            var elseSide = sideOf(ifThenElse.getElse());
            var leaves = new ArrayList<>(thenSide.leaves());
            leaves.addAll(elseSide.leaves());
            return new ClockSide(leaves, thenSide.everyBranchIsClock() && elseSide.everyBranchIsClock());
        }
        if (expression != null && referencedClock(expression) != null) {
            return new ClockSide(List.of(expression), true);
        }
        return new ClockSide(List.of(), false);
    }

    public List<ComparisonOperator> unclosedTests(TransitionDeclaration transition) {
        var unclosed = new ArrayList<ComparisonOperator>();
        for (var branch : transition.getBranches()) {
            visit(branch, new HashSet<>(), unclosed);
        }
        return unclosed;
    }

    private Set<ClockPath> visit(Operation operation, Set<ClockPath> reset, List<ComparisonOperator> unclosed) {
        switch (operation) {
            case SequenceOperation sequence -> {
                var current = reset;
                for (var step : sequence.getSteps()) {
                    current = visit(step, current, unclosed);
                }
                return current;
            }
            case ChoiceOperation choice -> {
                Set<ClockPath> common = null;
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
                var path = clock == null ? null : pathOf(assignment.getReference());
                if (path == null) {
                    return reset;
                }
                var updated = new HashSet<>(reset);
                updated.add(path);
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

    private Set<ClockPath> visitBranches(
            SequenceOperation body,
            SequenceOperation elseBody,
            Set<ClockPath> reset,
            List<ComparisonOperator> unclosed) {
        var thenReset = visit(body, new HashSet<>(reset), unclosed);
        var elseReset = elseBody == null ? reset : visit(elseBody, new HashSet<>(reset), unclosed);
        return intersect(thenReset, elseReset);
    }

    private static Set<ClockPath> intersect(Set<ClockPath> left, Set<ClockPath> right) {
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
            Set<ClockPath> reset,
            List<ComparisonOperator> unclosed) {
        if (expression == null) {
            return;
        }
        switch (expression) {
            case ComparisonOperator comparison -> {
                var test = clockTest(comparison);
                if (test == null) {
                    collectChildren(comparison, inAssumption, reset, unclosed);
                } else if (!isClosed(test, polarity, inAssumption) && !isReset(test, reset)) {
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
            default -> collectChildren(expression, inAssumption, reset, unclosed);
        }
    }

    private void collectChildren(
            EObject container, boolean inAssumption, Set<ClockPath> reset, List<ComparisonOperator> unclosed) {
        for (var child : container.eContents()) {
            if (child instanceof Expression childExpression) {
                collect(childExpression, null, inAssumption, reset, unclosed);
            } else {
                collectChildren(child, inAssumption, reset, unclosed);
            }
        }
    }

    private static boolean isClosed(ClockTest test, Boolean polarity, boolean inAssumption) {
        if (!inAssumption || polarity == null) {
            return false;
        }
        var op = polarity ? test.operator() : negated(test.operator());
        return op == ComparisonOp.LESS_EQ || op == ComparisonOp.GREATER_EQ || op == ComparisonOp.EQ;
    }

    private static ComparisonOp mirrored(ComparisonOp op) {
        return switch (op) {
            case LESS -> ComparisonOp.GREATER;
            case LESS_EQ -> ComparisonOp.GREATER_EQ;
            case GREATER -> ComparisonOp.LESS;
            case GREATER_EQ -> ComparisonOp.LESS_EQ;
            case EQ -> ComparisonOp.EQ;
            case NOT_EQ -> ComparisonOp.NOT_EQ;
        };
    }

    public static ComparisonOp negated(ComparisonOp op) {
        return switch (op) {
            case LESS -> ComparisonOp.GREATER_EQ;
            case LESS_EQ -> ComparisonOp.GREATER;
            case GREATER -> ComparisonOp.LESS_EQ;
            case GREATER_EQ -> ComparisonOp.LESS;
            case EQ -> ComparisonOp.NOT_EQ;
            case NOT_EQ -> ComparisonOp.EQ;
        };
    }

    private static boolean isReset(ClockTest test, Set<ClockPath> reset) {
        for (var leaf : test.clockLeaves()) {
            var path = pathOf(leaf);
            if (path == null || !reset.contains(path)) {
                return false;
            }
        }
        return true;
    }

    private static ClockPath pathOf(Expression expression) {
        return switch (expression) {
            case ElementReference reference -> new ClockPath(false, List.of(reference.getElement()));
            case SelfReference ignored -> new ClockPath(true, List.of());
            case NavigationSuffixExpression navigation -> {
                var primary = pathOf(navigation.getPrimary());
                yield primary == null ? null : primary.extended(navigation.getMember());
            }
            default -> null;
        };
    }
}
