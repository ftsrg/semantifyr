/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.util.Strings;

@Singleton
public class OxstsDocumentSymbolDetailsProvider extends DocumentSymbolMapper.DocumentSymbolDetailsProvider {

    @Override
    public String getDetails(EObject object) {
        return switch (object) {
            case ClassDeclaration classDeclaration -> getSuperTypesDetail(classDeclaration);
            case VariableDeclaration variable -> getText(variable.getTypeSpecification());
            case FeatureDeclaration feature -> getFeatureDetail(feature);
            case TransitionDeclaration transition -> getParametersDetail(transition);
            case PropertyDeclaration property -> getPropertyDetail(property);
            case AnnotationDeclaration annotation -> getAnnotationDetail(annotation);
            case null, default -> "";
        };
    }

    private String getSuperTypesDetail(ClassDeclaration classDeclaration) {
        var superTypes = getFeatureText(classDeclaration, OxstsPackage.Literals.CLASS_DECLARATION__SUPER_TYPES);
        if (superTypes.isEmpty()) {
            return "";
        }
        return ": " + superTypes;
    }

    private String getFeatureDetail(FeatureDeclaration feature) {
        var kind = getFeatureText(feature, OxstsPackage.Literals.FEATURE_DECLARATION__KIND);
        return kind + " " + getText(feature.getTypeSpecification());
    }

    private String getPropertyDetail(PropertyDeclaration property) {
        if (Strings.isEmpty(property.getName())) {
            return "";
        }
        return getParametersDetail(property) + ": " + getText(property.getReturnType());
    }

    private String getAnnotationDetail(AnnotationDeclaration annotation) {
        if (annotation.getParameters().isEmpty()) {
            return "";
        }
        return getParametersDetail(annotation);
    }

    private String getParametersDetail(ParametricDeclaration declaration) {
        return declaration.getParameters().stream().map(this::getText).collect(Collectors.joining(", ", "(", ")"));
    }

    private String getFeatureText(EObject object, EStructuralFeature feature) {
        return NodeModelUtils.findNodesForFeature(object, feature).stream()
                .map(NodeModelUtils::getTokenText)
                .collect(Collectors.joining(", "));
    }

    private String getText(EObject object) {
        if (object == null) {
            return "";
        }
        var node = NodeModelUtils.findActualNodeFor(object);
        if (node == null) {
            return "";
        }
        return NodeModelUtils.getTokenText(node);
    }
}
