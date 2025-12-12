/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.domain;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;

import java.util.List;

public class MergedDomainMemberCollection extends DomainMemberCollection {

    public MergedDomainMemberCollection(List<DomainMemberCollection> parents, RedefinitionHandler redefinitionHandler) {
        super(redefinitionHandler);

        throw new IllegalStateException("Not yet implemented!");
    }

    @Override
    public String toString() {
        return "MERGED#" ;
    }
}
