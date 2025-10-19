/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.domain;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public class InheritanceAwareDomainMemberCollection extends DomainMemberCollection {

    private final DomainMemberCollection parent;
    private final DomainDeclaration domainDeclaration;

    public InheritanceAwareDomainMemberCollection(DomainDeclaration domainDeclaration, DomainMemberCollection parent, RedefinitionHandler redefinitionHandler) {
        super(parent, redefinitionHandler);

        this.domainDeclaration = domainDeclaration;
        this.parent = parent;

        addMembers(OxstsUtils.getDirectMembers(domainDeclaration));
    }

    @Override
    public String toString() {
        return "'" + domainDeclaration.getName() + "'" + " -> " + parent.toString();
    }

}
