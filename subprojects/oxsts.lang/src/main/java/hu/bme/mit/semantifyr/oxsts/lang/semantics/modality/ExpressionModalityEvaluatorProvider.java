/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.modality;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import org.eclipse.emf.ecore.EObject;

public class ExpressionModalityEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    private Provider<ExpressionModalityEvaluator> expressionModalityEvaluatorProvider;

    public ExpressionModalityEvaluator getEvaluator(EObject eObject) {
        return resourceScopeCache.get(CACHE_KEY, eObject.eResource(), expressionModalityEvaluatorProvider);
    }

    public Modality evaluate(Expression expression) {
        return getEvaluator(expression).evaluate(expression);
    }

}
