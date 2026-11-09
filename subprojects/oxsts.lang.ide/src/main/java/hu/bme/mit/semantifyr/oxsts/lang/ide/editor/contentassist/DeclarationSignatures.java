/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist;

import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

public final class DeclarationSignatures {

    private DeclarationSignatures() {}

    public static String type(TypeSpecification typeSpecification) {
        if (typeSpecification == null) {
            return null;
        }
        var node = NodeModelUtils.findActualNodeFor(typeSpecification);
        if (node != null) {
            return NodeModelUtils.getTokenText(node);
        }
        return NamingUtil.getName(typeSpecification.getDomain());
    }

    public static String parameters(ParametricDeclaration declaration) {
        return declaration.getParameters().stream()
                .map(parameter -> parameter.getName() + ": " + type(parameter.getTypeSpecification()))
                .collect(Collectors.joining(", "));
    }

    public static String signature(ParametricDeclaration declaration) {
        var signature = NamingUtil.getName(declaration) + "(" + parameters(declaration) + ")";
        if (declaration instanceof PropertyDeclaration property && property.getReturnType() != null) {
            return signature + ": " + type(property.getReturnType());
        }
        return signature;
    }

    public static String detail(EObject element) {
        return switch (element) {
            case TransitionDeclaration transition -> signature(transition);
            case PropertyDeclaration property -> signature(property);
            case AnnotationDeclaration annotation -> signature(annotation);
            case FeatureDeclaration feature -> keyword(feature.getKind()) + " " + type(feature.getTypeSpecification());
            case VariableDeclaration variable -> type(variable.getTypeSpecification());
            case ParameterDeclaration parameter -> type(parameter.getTypeSpecification());
            default -> null;
        };
    }

    public static String keyword(FeatureKind kind) {
        return switch (kind) {
            case REFERENCE -> "refers";
            case CONTAINMENT -> "contains";
            case CONTAINER -> "container";
            case DERIVED -> "derived refers";
            case FEATURE -> "features";
        };
    }

    public static String keyword(TransitionKind kind) {
        return switch (kind) {
            case TRAN -> "tran";
            case INIT -> "init";
        };
    }
}
