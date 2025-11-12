/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import org.eclipse.emf.ecore.EObject;

public class MetaConstantExpressionEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    protected Provider<MetaConstantExpressionEvaluator> metaConstantExpressionEvaluatorProvider;

    public MetaConstantExpressionEvaluator getEvaluator(EObject context) {
        return resourceScopeCache.get(CACHE_KEY, context.eResource(), metaConstantExpressionEvaluatorProvider);
    }

    public NamedElement evaluate(Expression expression) {
        return getEvaluator(expression).evaluate(expression);
    }

}
