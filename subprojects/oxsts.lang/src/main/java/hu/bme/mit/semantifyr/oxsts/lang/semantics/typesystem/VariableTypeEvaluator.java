/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class VariableTypeEvaluator {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.VariableTypeEvaluator.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    private ExpressionTypeEvaluatorProvider expressionTypeEvaluatorProvider;

    public TypeEvaluation evaluate(VariableDeclaration variableDeclaration) {
        return cache.get(Tuples.create(CACHE_KEY, variableDeclaration), variableDeclaration.eResource(), () -> computeTypeOf(variableDeclaration));
    }

    private TypeEvaluation computeTypeOf(VariableDeclaration variableDeclaration) {
        var domainDeclaration = variableDeclaration.getType();

        if (domainDeclaration == null) {
            return computeImplicitTypeOf(variableDeclaration);
        }

        return new ImmutableTypeEvaluation(domainDeclaration);
    }

    private TypeEvaluation computeImplicitTypeOf(VariableDeclaration variableDeclaration) {
        if (variableDeclaration.getExpression() != null) {
            return expressionTypeEvaluatorProvider.evaluateExpressionType(variableDeclaration.getExpression());
        }

        if (variableDeclaration.eContainer() instanceof AbstractForOperation abstractForOperation) {
            // TODO: should get the 'type of an element of the rangeExpression'
            return expressionTypeEvaluatorProvider.evaluateExpressionType(abstractForOperation.getRangeExpression());
        }

        return TypeEvaluation.INVALID;
    }

}
