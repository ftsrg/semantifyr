/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import com.google.common.collect.Iterables;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

import java.util.ArrayList;
import java.util.List;

public class OxstsUtils {
    public static final String LIBRARY_EXTENSION = "oxsts";

    private OxstsUtils() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static Iterable<Declaration> getDirectMembers(Declaration declaration) {
        return switch (declaration) {
            case InlinedOxsts decl -> getDirectMembers(decl);
            case RecordDeclaration decl -> getDirectMembers(decl);
            case ClassDeclaration decl -> getDirectMembers(decl);
            case FeatureDeclaration decl -> getDirectMembers(decl);
            default -> List.of();
        };
    }

    public static Iterable<Declaration> getDirectMembers(InlinedOxsts inlinedOxsts) {
        var elements = new ArrayList<Declaration>();

        if (inlinedOxsts.getRootFeature() != null) {
            elements.add(inlinedOxsts.getRootFeature());
        }
        if (inlinedOxsts.getInitTransition() != null) {
            elements.add(inlinedOxsts.getInitTransition());
        }
        if (inlinedOxsts.getMainTransition() != null) {
            elements.add(inlinedOxsts.getMainTransition());
        }
        if (inlinedOxsts.getProperty() != null) {
            elements.add(inlinedOxsts.getProperty());
        }

        return Iterables.concat(inlinedOxsts.getVariables(), elements);
    }

    public static Iterable<Declaration> getDirectMembers(RecordDeclaration declaration) {
        return declaration.getMembers();
    }

    public static Iterable<Declaration> getDirectMembers(ClassDeclaration declaration) {
        return declaration.getMembers();
    }

    public static Iterable<Declaration> getDirectMembers(FeatureDeclaration declaration) {
        return declaration.getInnerFeatures().stream().map(f -> (Declaration) f).toList();
    }

    public static boolean isAnnotatedWith(AnnotatedElement element, AnnotationDeclaration annotationDeclaration) {
        var annotationContainer = element.getAnnotation();

        return annotationContainer.getAnnotations().stream().anyMatch(a -> a.getDeclaration() == annotationDeclaration);
    }

    public static Annotation getAnnotation(AnnotatedElement element, AnnotationDeclaration annotationDeclaration) {
        var annotationContainer = element.getAnnotation();

        return annotationContainer.getAnnotations().stream().filter(a -> a.getDeclaration() == annotationDeclaration).findFirst().orElse(null);
    }

    public static Expression getAnnotationValue(Annotation annotation, ParameterDeclaration parameterDeclaration) {
        var argument = annotation.getArguments().stream().filter(a -> a.getParameter() == parameterDeclaration).findFirst().orElse(null);

        if (argument == null) {
            var position = annotation.getDeclaration().getParameters().indexOf(parameterDeclaration);
            argument = annotation.getArguments().get(position);
        }

        return argument.getExpression();
    }

}
