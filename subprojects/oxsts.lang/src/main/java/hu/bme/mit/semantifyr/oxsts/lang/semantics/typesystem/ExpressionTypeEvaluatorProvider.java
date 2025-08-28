/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import org.eclipse.emf.ecore.EObject;

@Singleton
public class ExpressionTypeEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    private Provider<ExpressionTypeEvaluator> expressionTypeEvaluatorProvider;

    public ExpressionTypeEvaluator getExpressionTypeEvaluator(EObject eObject) {
        return resourceScopeCache.get(CACHE_KEY, eObject.eResource(), expressionTypeEvaluatorProvider);
    }

    public TypeEvaluation evaluateExpressionType(Expression expression) {
        return getExpressionTypeEvaluator(expression).evaluate(expression);
    }

}
