/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DefiniteMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnboundedMultiplicity;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class MultiplicityRangeEvaluator {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.MultiplicityRangeProvider.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected ConstantExpressionEvaluatorProvider constantExpressionEvaluatorProvider;

    public RangeEvaluation evaluate(FeatureDeclaration featureDeclaration) {
        return cache.get(Tuples.create(CACHE_KEY, featureDeclaration), featureDeclaration.eResource(), () -> compute(featureDeclaration));
    }

    protected RangeEvaluation compute(FeatureDeclaration featureDeclaration) {
        var multiplicity = featureDeclaration.getMultiplicity();

        return switch (multiplicity) {
            case null -> RangeEvaluation.ONE; // implicit multiplicity
            case UnboundedMultiplicity ignored -> RangeEvaluation.UNBOUNDED;
            case DefiniteMultiplicity definiteMultiplicity -> compute(definiteMultiplicity);
            default -> throw new IllegalArgumentException("Unexpected type of multiplicity!");
        };
    }

    protected RangeEvaluation compute(DefiniteMultiplicity definiteMultiplicity) {
        var evaluation = constantExpressionEvaluatorProvider.evaluate(definiteMultiplicity.getExpression());

        if (evaluation instanceof RangeEvaluation rangeEvaluation) {
            return rangeEvaluation;
        }

        if (evaluation instanceof IntegerEvaluation(int value)) {
            return RangeEvaluation.of(value, value);
        }

        throw new IllegalArgumentException("Expression could not be evaluated to a range!");
    }

}
