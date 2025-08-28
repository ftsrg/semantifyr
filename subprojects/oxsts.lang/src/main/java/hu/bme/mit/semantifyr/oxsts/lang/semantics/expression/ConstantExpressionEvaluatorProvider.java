/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import org.eclipse.emf.ecore.EObject;

@Singleton
public class ConstantExpressionEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    protected Provider<ConstantExpressionEvaluator> constantExpressionEvaluatorProvider;

    public ConstantExpressionEvaluator getEvaluator(EObject context) {
        return resourceScopeCache.get(CACHE_KEY, context.eResource(), constantExpressionEvaluatorProvider);
    }

    public ExpressionEvaluation evaluate(Expression expression) {
        return getEvaluator(expression).evaluate(expression);
    }

}
