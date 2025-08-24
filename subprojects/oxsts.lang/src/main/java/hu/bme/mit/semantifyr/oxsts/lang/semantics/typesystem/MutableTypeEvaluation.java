/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public final class MutableTypeEvaluation implements TypeEvaluation {

    private DomainDeclaration domainDeclaration;

    public MutableTypeEvaluation(DomainDeclaration domainDeclaration) {
        this.domainDeclaration = domainDeclaration;
    }

    @Override
    public DomainDeclaration getDomain() {
        return domainDeclaration;
    }

    public void setDomain(DomainDeclaration domainDeclaration) {
        this.domainDeclaration = domainDeclaration;
    }


}
