/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class OxstsUtils {
    public static final String LIBRARY_EXTENSION = "oxsts";

    private OxstsUtils() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static boolean isConstantLiteralFalse(Expression expression) {
        if (expression instanceof LiteralBoolean literalBoolean) {
            return ! literalBoolean.isValue();
        }

        return false;
    }

    public static boolean isConstantLiteralTrue(Expression expression) {
        if (expression instanceof LiteralBoolean literalBoolean) {
            return literalBoolean.isValue();
        }

        return false;
    }

    public static boolean isGlobalFeature(EObject element) {
        return element instanceof FeatureDeclaration featureDeclaration && featureDeclaration.eContainer() instanceof OxstsModelPackage;
    }

    public static boolean isLoopVariable(EObject element) {
        var container = element.eContainer();

        if (container instanceof AbstractForOperation forOperation) {
            return forOperation.getLoopVariable() == element;
        }

        return false;
    }

    public static boolean isLocalVariable(EObject element) {
        return element instanceof LocalVarDeclarationOperation;
    }

    public static boolean isReferenceContextual(ElementReference elementReference) {
        var element = elementReference.getElement();

        return isElementContextual(element);
    }

    public static boolean isElementContextual(Element element) {
        if (isLoopVariable(element) || isLocalVariable(element) || isGlobalFeature(element)) {
            return false;
        }

        return element instanceof FeatureDeclaration
                || element instanceof VariableDeclaration
                || element instanceof PropertyDeclaration
                || element instanceof TransitionDeclaration;
    }

    public static List<? extends Declaration> getDirectMembers(Declaration declaration) {
        return switch (declaration) {
            case InlinedOxsts decl -> getDirectMembers(decl);
            case RecordDeclaration decl -> getDirectMembers(decl);
            case ClassDeclaration decl -> getDirectMembers(decl);
            case FeatureDeclaration decl -> getDirectMembers(decl);
            default -> List.of();
        };
    }

    public static List<FeatureDeclaration> getGlobalFeatures(EObject context) {
        var resourceSet = context.eResource().getResourceSet();

        var globalFeatures = resourceSet.getResources().stream()
                .flatMap(r -> Stream.of(r.getContents().getFirst()))
                .filter(e -> e instanceof OxstsModelPackage).map(e -> (OxstsModelPackage) e)
                .flatMap(p -> p.getDeclarations().stream())
                .filter(e -> e instanceof FeatureDeclaration).map(e -> (FeatureDeclaration) e);

        return globalFeatures.toList();
    }

    public static List<? extends Declaration> getDirectMembers(InlinedOxsts inlinedOxsts) {
        var elements = new ArrayList<Declaration>(inlinedOxsts.getVariables());

        if (inlinedOxsts.getRootFeature() != null) {
            elements.add(inlinedOxsts.getRootFeature());
        }

        elements.addAll(getGlobalFeatures(inlinedOxsts));

        return elements;
    }

    public static List<? extends Declaration> getDirectMembers(RecordDeclaration declaration) {
        return declaration.getMembers();
    }

    public static List<? extends Declaration> getDirectMembers(ClassDeclaration declaration) {
        return declaration.getMembers();
    }

    public static List<? extends Declaration> getDirectMembers(FeatureDeclaration declaration) {
        return declaration.getInnerFeatures();
    }

    public static boolean isAnnotatedWith(AnnotatedElement element, AnnotationDeclaration annotationDeclaration) {
        var annotationContainer = element.getAnnotation();

        if (annotationContainer == null) {
            return false;
        }

        return annotationContainer.getAnnotations().stream().anyMatch(a -> a.getDeclaration() == annotationDeclaration);
    }

    public static Annotation getAnnotation(AnnotatedElement element, AnnotationDeclaration annotationDeclaration) {
        var annotationContainer = element.getAnnotation();

        if (annotationContainer == null) {
            return null;
        }

        return annotationContainer.getAnnotations().stream().filter(a -> a.getDeclaration() == annotationDeclaration).findFirst().orElse(null);
    }

    public static Expression getAnnotationValue(Annotation annotation, ParameterDeclaration parameterDeclaration) {
        var argument = annotation.getArguments().stream().filter(a -> a.getParameter() == parameterDeclaration).findFirst().orElse(null);

        if (argument == null) {
            var position = annotation.getDeclaration().getParameters().indexOf(parameterDeclaration);
            if (annotation.getArguments().size() <= position) {
                return null;
            }
            argument = annotation.getArguments().get(position);
        }

        return argument.getExpression();
    }

}
