/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import org.eclipse.emf.ecore.EObject;

@Singleton
public class TypeEvaluatorProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.typesystem.TypeSystemModule.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    public TypeEvaluator getTypeEvaluator(EObject eObject) {
        var resource = eObject.eResource();
        return resourceScopeCache.get(CACHE_KEY, resource, TypeEvaluator::new);
    }

    public TypeEvaluation evaluateExpression(Expression expression) {
        var evaluator = getTypeEvaluator(expression);
        return evaluator.evaluateExpressionType(expression);
    }

}
