/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public record ImmutableTypeEvaluation(DomainDeclaration domainDeclaration) implements TypeEvaluation {

    @Override
    public DomainDeclaration getDomain() {
        return domainDeclaration;
    }

}
