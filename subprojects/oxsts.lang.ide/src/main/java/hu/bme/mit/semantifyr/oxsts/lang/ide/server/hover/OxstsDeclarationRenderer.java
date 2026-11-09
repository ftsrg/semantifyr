/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.hover;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DefiniteMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Multiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeSpecification;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnboundedMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

public class OxstsDeclarationRenderer {

    @Inject
    private ExpressionSerializer expressionSerializer;

    public String render(EObject element) {
        return switch (element) {
            case OxstsModelPackage modelPackage -> "package " + modelPackage.getName();
            case ClassDeclaration classDeclaration -> renderClass(classDeclaration);
            case EnumDeclaration enumDeclaration -> "enum " + enumDeclaration.getName();
            case EnumLiteral literal -> "enum literal " + NamingUtil.getName(literal.eContainer()) + "::" + literal.getName();
            case RecordDeclaration record -> "record " + record.getName();
            case DataTypeDeclaration dataType -> "extern datatype " + dataType.getName();
            case AnnotationDeclaration annotation -> renderAnnotation(annotation);
            case FeatureDeclaration feature -> renderFeature(feature);
            case VariableDeclaration variable -> renderVariable(variable);
            case TransitionDeclaration transition -> renderTransition(transition);
            case PropertyDeclaration property -> renderProperty(property);
            case ParameterDeclaration parameter -> "parameter " + renderParameter(parameter);
            default -> null;
        };
    }

    public String renderParameter(ParameterDeclaration parameter) {
        return parameter.getName() + ": " + renderTypeSpecification(parameter.getTypeSpecification());
    }

    public String renderParameters(ParametricDeclaration declaration) {
        return declaration.getParameters().stream().map(this::renderParameter).collect(Collectors.joining(", "));
    }

    protected String renderClass(ClassDeclaration classDeclaration) {
        var text = new StringBuilder();
        if (classDeclaration.isAbstract()) {
            text.append("abstract ");
        }
        text.append("class ").append(classDeclaration.getName());
        if (!classDeclaration.getSuperTypes().isEmpty()) {
            var superTypes = classDeclaration.getSuperTypes().stream()
                    .map(NamingUtil::getName)
                    .collect(Collectors.joining(", "));
            text.append(" : ").append(superTypes);
        }
        return text.toString();
    }

    protected String renderAnnotation(AnnotationDeclaration annotation) {
        if (annotation.getParameters().isEmpty()) {
            return "annotation " + annotation.getName();
        }
        return "annotation " + annotation.getName() + "(" + renderParameters(annotation) + ")";
    }

    protected String renderFeature(FeatureDeclaration feature) {
        var text = new StringBuilder(renderRedefine(feature));
        if (feature.eContainer() instanceof OxstsModelPackage) {
            text.append(feature.getKind() == FeatureKind.CONTAINMENT ? "global containment" : "global reference");
        } else {
            text.append(renderFeatureKind(feature.getKind()));
        }
        text.append(" ").append(feature.getName());
        text.append(": ").append(renderTypeSpecification(feature.getTypeSpecification()));
        if (feature.getSuperset() != null) {
            text.append(" subsets ").append(NamingUtil.getName(feature.getSuperset()));
        }
        if (feature.getOpposite() != null) {
            text.append(" opposite ").append(NamingUtil.getName(feature.getOpposite()));
        }
        return text.toString();
    }

    protected String renderFeatureKind(FeatureKind kind) {
        return switch (kind) {
            case REFERENCE -> "refers";
            case CONTAINMENT -> "contains";
            case CONTAINER -> "container";
            case DERIVED -> "derived refers";
            case FEATURE -> "features";
        };
    }

    protected String renderVariable(VariableDeclaration variable) {
        if (variable.getTypeSpecification() == null) {
            return "var " + variable.getName();
        }
        return "var " + variable.getName() + ": " + renderTypeSpecification(variable.getTypeSpecification());
    }

    protected String renderTransition(TransitionDeclaration transition) {
        var text = new StringBuilder(renderRedefine(transition));
        if (transition.isAbstract()) {
            text.append("abstract ");
        }
        text.append(transition.getKind() == TransitionKind.INIT ? "init" : "tran");
        if (transition.getName() != null) {
            text.append(" ").append(transition.getName());
            text.append("(").append(renderParameters(transition)).append(")");
        }
        return text.toString();
    }

    protected String renderProperty(PropertyDeclaration property) {
        var text = new StringBuilder(renderRedefine(property));
        if (property.isAbstract()) {
            text.append("abstract ");
        }
        text.append("prop");
        if (property.getName() != null) {
            text.append(" ").append(property.getName());
            text.append("(").append(renderParameters(property)).append(")");
            text.append(": ").append(renderTypeSpecification(property.getReturnType()));
        }
        return text.toString();
    }

    protected String renderRedefine(RedefinableDeclaration declaration) {
        if (declaration.isRedefine()) {
            return "redefine ";
        }
        return "";
    }

    public String renderTypeSpecification(TypeSpecification typeSpecification) {
        if (typeSpecification == null) {
            return "?";
        }
        var domain = typeSpecification.getDomain();
        if (domain == null || domain.eIsProxy()) {
            var node = NodeModelUtils.getNode(typeSpecification);
            if (node == null) {
                return "?";
            }
            return NodeModelUtils.getTokenText(node);
        }
        return NamingUtil.getName(domain) + renderMultiplicity(typeSpecification.getMultiplicity());
    }

    protected String renderMultiplicity(Multiplicity multiplicity) {
        return switch (multiplicity) {
            case UnboundedMultiplicity ignored -> "[]";
            case DefiniteMultiplicity definite -> "[" + expressionSerializer.serialize(definite.getExpression()) + "]";
            case null, default -> "";
        };
    }
}
