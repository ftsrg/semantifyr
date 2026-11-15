/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DefiniteMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnboundedMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import org.eclipse.xtext.util.Tuples;

public class MultiplicityRangeEvaluator {
    private static final String CACHE_KEY =
            "hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    protected ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    public RangeEvaluation evaluate(TypeSpecification typeSpecification) {
        return cache.get(
                Tuples.create(CACHE_KEY, typeSpecification),
                typeSpecification.eResource(),
                () -> compute(typeSpecification));
    }

    protected RangeEvaluation compute(TypeSpecification typeSpecification) {
        var multiplicity = typeSpecification.getMultiplicity();

        return switch (multiplicity) {
            case null -> RangeEvaluation.ONE; // implicit multiplicity
            case UnboundedMultiplicity ignored -> RangeEvaluation.UNBOUNDED;
            case DefiniteMultiplicity definiteMultiplicity -> compute(definiteMultiplicity);
            default -> throw new IllegalArgumentException("Unexpected type of multiplicity!");
        };
    }

    protected RangeEvaluation compute(DefiniteMultiplicity definiteMultiplicity) {
        ExpressionEvaluation evaluation;
        try {
            evaluation = constantExpressionEvaluatorProvider.evaluate(definiteMultiplicity.getExpression());
        } catch (EvaluationFailureException e) {
            if (!holdsValues(definiteMultiplicity)) {
                throw e;
            }
            return RangeEvaluation.perInstance(lowerBoundOf(definiteMultiplicity.getExpression()));
        }

        if (evaluation instanceof RangeEvaluation rangeEvaluation) {
            return rangeEvaluation;
        }

        if (evaluation instanceof IntegerEvaluation(int value)) {
            return RangeEvaluation.of(value, value);
        }

        throw new IllegalArgumentException("Expression could not be evaluated to a range!");
    }

    private static boolean holdsValues(DefiniteMultiplicity definiteMultiplicity) {
        var holder = definiteMultiplicity.eContainer() == null
                ? null
                : definiteMultiplicity.eContainer().eContainer();
        return holder instanceof VariableDeclaration || holder instanceof ParameterDeclaration;
    }

    // a lower bound of none admits nothing in a slot; one that is not constant is taken as one, never as none
    private long lowerBoundOf(Expression expression) {
        if (!(expression instanceof RangeExpression range)) {
            return 1;
        }
        try {
            if (constantExpressionEvaluatorProvider.evaluate(range.getLeft()) instanceof IntegerEvaluation(int value)) {
                return value;
            }
        } catch (EvaluationFailureException e) {
            return 1;
        }
        return 1;
    }
}
