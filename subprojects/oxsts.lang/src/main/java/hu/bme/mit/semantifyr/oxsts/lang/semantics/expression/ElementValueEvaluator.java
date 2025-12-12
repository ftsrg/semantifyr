/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ElementValueEvaluator<T> {

    protected final Map<Element, T> evaluations = new HashMap<>();
    protected final Set<Element> underEvaluation = new HashSet<>();

    public T evaluate(Element element) {
        var evaluation = evaluations.get(element);

        // cannot use computeIfAbsent due to concurrent modification (recursive calL!)
        if (evaluation == null) {
            if (! underEvaluation.add(element)) {
                throw new IllegalStateException("Circular dependency encountered during element value evaluation!");
            }

            try {
                evaluation = visit(element);
            } finally {
                underEvaluation.remove(element);
            }

            evaluations.put(element, evaluation);
        }

        return evaluation;
    }

    protected abstract T visit(Element element);

}
