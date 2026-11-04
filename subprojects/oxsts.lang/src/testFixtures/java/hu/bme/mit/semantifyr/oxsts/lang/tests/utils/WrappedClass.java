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
                .map(member -> new WrappedFeature((FeatureDeclaration) member))
                .toList();
    }

    public WrappedFeature featureByName(String name) {
        return features().stream()
                .filter(feature -> name.equals(feature.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No feature named '" + name + "' in class '" + name() + "'"));
    }

    public List<WrappedVariable> variables() {
        return eObject.getMembers().stream()
                .filter(VariableDeclaration.class::isInstance)
                .map(member -> new WrappedVariable((VariableDeclaration) member))
                .toList();
    }

    public WrappedVariable variableByName(String name) {
        return variables().stream()
                .filter(variable -> name.equals(variable.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No variable named '" + name + "' in class '" + name() + "'"));
    }

    public List<WrappedTransition> transitions() {
        return eObject.getMembers().stream()
                .filter(TransitionDeclaration.class::isInstance)
                .map(member -> new WrappedTransition((TransitionDeclaration) member))
                .toList();
    }

    public WrappedTransition namedTransition(String name) {
        return transitions().stream()
                .filter(transition -> name.equals(transition.name()))
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
                .filter(transition -> transition.eObject().getName() == null)
                .filter(transition -> transition.eObject().getKind() == kind)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("No anonymous " + kind + " transition in class '" + name() + "'"));
    }

    public List<WrappedProperty> properties() {
        return eObject.getMembers().stream()
                .filter(PropertyDeclaration.class::isInstance)
                .map(member -> new WrappedProperty((PropertyDeclaration) member))
                .toList();
    }

    public WrappedProperty anonymousProperty() {
        return properties().stream()
                .filter(property -> property.eObject().getName() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No anonymous prop in class '" + name() + "'"));
    }

    public WrappedProperty namedProperty(String name) {
        return properties().stream()
                .filter(property -> name.equals(property.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No named property '" + name + "' in class '" + name() + "'"));
    }
}
