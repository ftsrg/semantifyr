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

public class OxstsUtils {
    public static final String LIBRARY_EXTENSION = "oxsts";

    private OxstsUtils() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
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

    public static List<? extends Declaration> getDirectMembers(InlinedOxsts inlinedOxsts) {
        var elements = new ArrayList<Declaration>(inlinedOxsts.getVariables());

        if (inlinedOxsts.getRootFeature() != null) {
            elements.add(inlinedOxsts.getRootFeature());
        }

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
