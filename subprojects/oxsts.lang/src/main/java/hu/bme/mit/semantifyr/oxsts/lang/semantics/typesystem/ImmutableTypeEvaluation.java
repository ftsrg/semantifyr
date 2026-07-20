/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public record ImmutableTypeEvaluation(DomainDeclaration domainDeclaration, RangeEvaluation rangeEvaluation)
        implements TypeEvaluation {

    public ImmutableTypeEvaluation(DomainDeclaration domainDeclaration) {
        this(domainDeclaration, RangeEvaluation.ONE);
    }

    @Override
    public DomainDeclaration getDomain() {
        return domainDeclaration;
    }

    @Override
    public RangeEvaluation getRange() {
        return rangeEvaluation;
    }
}
