/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

import java.util.HashMap;
import java.util.Map;

public abstract class ExpressionEvaluator<T> extends ExpressionVisitor<T> {

    protected final Map<Expression, T> evaluations = new HashMap<>();

    public T evaluate(Expression expression) {
        var evaluation = evaluations.get(expression);

        // cannot use computeIfAbsent due to concurrent modification (recursive calL!)
        if (evaluation == null) {
            evaluation = visit(expression);
            evaluations.put(expression, evaluation);
        }

        return evaluation;
    }

}
