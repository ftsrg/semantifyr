/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;

import java.util.List;

public record WrappedFeature(FeatureDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public FeatureKind kind() {
        return eObject.getKind();
    }

    public boolean isRedefine() {
        return eObject.isRedefine();
    }

    public TypeSpecification typeSpecification() {
        return eObject.getTypeSpecification();
    }

    public DomainDeclaration typeDomain() {
        return typeSpecification() != null ? typeSpecification().getDomain() : null;
    }

    public WrappedFeature superset() {
        return eObject.getSuperset() != null ? new WrappedFeature(eObject.getSuperset()) : null;
    }

    public WrappedFeature opposite() {
        return eObject.getOpposite() != null ? new WrappedFeature(eObject.getOpposite()) : null;
    }

    public Expression expression() {
        return eObject.getExpression();
    }

    public List<WrappedFeature> innerFeatures() {
        return eObject.getInnerFeatures().stream().map(WrappedFeature::new).toList();
    }
}
