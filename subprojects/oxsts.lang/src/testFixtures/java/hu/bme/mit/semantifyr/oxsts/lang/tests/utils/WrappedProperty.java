/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;

import java.util.List;

public record WrappedProperty(PropertyDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public boolean isAbstract() {
        return eObject.isAbstract();
    }

    public boolean isRedefine() {
        return eObject.isRedefine();
    }

    public List<WrappedParameter> parameters() {
        return eObject.getParameters().stream().map(WrappedParameter::new).toList();
    }

    public TypeSpecification returnTypeSpecification() {
        return eObject.getReturnType();
    }

    public DomainDeclaration returnTypeDomain() {
        return returnTypeSpecification() != null ? returnTypeSpecification().getDomain() : null;
    }

    public Expression expression() {
        return eObject.getExpression();
    }
}
