/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;

import java.util.List;

public record WrappedClass(ClassDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public boolean isAbstract() {
        return eObject.isAbstract();
    }

    public List<WrappedClass> superTypes() {
        return eObject.getSuperTypes().stream().map(WrappedClass::new).toList();
    }

    public List<WrappedFeature> features() {
        return eObject.getMembers().stream()
            .filter(FeatureDeclaration.class::isInstance)
            .map(m -> new WrappedFeature((FeatureDeclaration) m))
            .toList();
    }

    public WrappedFeature featureByName(String name) {
        return features().stream()
            .filter(f -> name.equals(f.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No feature named '" + name + "' in class '" + name() + "'"));
    }

    public List<WrappedVariable> variables() {
        return eObject.getMembers().stream()
            .filter(VariableDeclaration.class::isInstance)
            .map(m -> new WrappedVariable((VariableDeclaration) m))
            .toList();
    }

    public WrappedVariable variableByName(String name) {
        return variables().stream()
            .filter(v -> name.equals(v.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No variable named '" + name + "' in class '" + name() + "'"));
    }

    public List<WrappedTransition> transitions() {
        return eObject.getMembers().stream()
            .filter(TransitionDeclaration.class::isInstance)
            .map(m -> new WrappedTransition((TransitionDeclaration) m))
            .toList();
    }

    public WrappedTransition namedTransition(String name) {
        return transitions().stream()
            .filter(t -> name.equals(t.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No named transition '" + name + "' in class '" + name() + "'"));
    }

    public WrappedTransition anonymousMain() {
        return anonymousTransitionOfKind(TransitionKind.TRAN);
    }

    public WrappedTransition anonymousInit() {
        return anonymousTransitionOfKind(TransitionKind.INIT);
    }

    private WrappedTransition anonymousTransitionOfKind(TransitionKind kind) {
        return transitions().stream()
            .filter(t -> t.eObject().getName() == null)
            .filter(t -> t.eObject().getKind() == kind)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No anonymous " + kind + " transition in class '" + name() + "'"));
    }

    public List<WrappedProperty> properties() {
        return eObject.getMembers().stream()
            .filter(PropertyDeclaration.class::isInstance)
            .map(m -> new WrappedProperty((PropertyDeclaration) m))
            .toList();
    }

    public WrappedProperty anonymousProperty() {
        return properties().stream()
            .filter(p -> p.eObject().getName() == null)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No anonymous prop in class '" + name() + "'"));
    }

    public WrappedProperty namedProperty(String name) {
        return properties().stream()
            .filter(p -> name.equals(p.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No named property '" + name + "' in class '" + name() + "'"));
    }
}
