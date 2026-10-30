/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;

public record WrappedParameter(ParameterDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public TypeSpecification typeSpecification() {
        return eObject.getTypeSpecification();
    }

    public DomainDeclaration typeDomain() {
        return typeSpecification() != null ? typeSpecification().getDomain() : null;
    }
}
