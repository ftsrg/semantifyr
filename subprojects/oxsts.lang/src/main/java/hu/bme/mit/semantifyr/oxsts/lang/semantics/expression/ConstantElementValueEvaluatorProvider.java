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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element;
import org.eclipse.emf.ecore.EObject;

@Singleton
public class ConstantElementValueEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantElementValueEvaluatorProvider.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    protected Provider<ConstantElementValueEvaluator> constantElementValueEvaluatorProvider;

    public ConstantElementValueEvaluator getEvaluator(EObject context) {
        return resourceScopeCache.get(CACHE_KEY, context.eResource(), constantElementValueEvaluatorProvider);
    }

    public ExpressionEvaluation evaluate(Element element) {
        return getEvaluator(element).evaluate(element);
    }

}
