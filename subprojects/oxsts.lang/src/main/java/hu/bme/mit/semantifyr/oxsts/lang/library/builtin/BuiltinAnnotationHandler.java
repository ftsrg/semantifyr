/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

@Singleton
public class BuiltinAnnotationHandler {

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    protected RedefinitionHandler redefinitionHandler;

    public boolean isControlVariable(VariableDeclaration variableDeclaration) {
        var controlAnnotation = builtinSymbolResolver.controlAnnotation(variableDeclaration);

        return OxstsUtils.isAnnotatedWith(variableDeclaration, controlAnnotation);
    }

    public boolean isSharedFeature(FeatureDeclaration featureDeclaration) {
        var sharedAnnotation = builtinSymbolResolver.sharedAnnotation(featureDeclaration);

        return OxstsUtils.isAnnotatedWith(featureDeclaration, sharedAnnotation);
    }

    public boolean isVerificationCase(ClassDeclaration classDeclaration) {
        var verificationCaseAnnotation = builtinSymbolResolver.verificationCaseAnnotation(classDeclaration);

        return OxstsUtils.isAnnotatedWith(classDeclaration, verificationCaseAnnotation);
    }

    public String getVerificationCaseSummary(ClassDeclaration classDeclaration) {
        var verificationCaseAnnotation = builtinSymbolResolver.verificationCaseAnnotation(classDeclaration);
        var verificationCaseAnnotationSummary = builtinSymbolResolver.verificationCaseAnnotationSummary(classDeclaration);
        var annotation = OxstsUtils.getAnnotation(classDeclaration, verificationCaseAnnotation);
        var value = OxstsUtils.getAnnotationValue(annotation, verificationCaseAnnotationSummary);

        if (value instanceof LiteralString literalString) {
            return literalString.getValue();
        }

        return null;
    }

    public boolean isTaggedWith(ClassDeclaration classDeclaration, String category) {
        var tagAnnotation = builtinSymbolResolver.tagAnnotation(classDeclaration);
        var tagAnnotationCategory = builtinSymbolResolver.tagAnnotationCategory(classDeclaration);
        var annotations = OxstsUtils.getAnnotations(classDeclaration, tagAnnotation);

        assert annotations != null;

        var values = annotations.map(annotation ->
                OxstsUtils.getAnnotationValue(annotation, tagAnnotationCategory)
        ).filter(value ->
                value instanceof LiteralString
        ).map(value ->
                ((LiteralString) value).getValue()
        );

        return values.anyMatch(category::equals);
    }

    public boolean isNotTaggedWith(ClassDeclaration classDeclaration, String category) {
        return ! isTaggedWith(classDeclaration, category);
    }

    public boolean isTransitionTraced(TransitionDeclaration transitionDeclaration) {
        var traceAnnotation = builtinSymbolResolver.traceAnnotation(transitionDeclaration);

        return isTransitivelyAnnotatedWith(transitionDeclaration, traceAnnotation);
    }

    protected boolean isTransitivelyAnnotatedWith(RedefinableDeclaration annotatedElement, AnnotationDeclaration annotation) {
        var element = annotatedElement;

        while (element != null) {
            if (OxstsUtils.isAnnotatedWith(element, annotation)) {
                return true;
            }
            element = redefinitionHandler.getRedefinedDeclaration(element);
        }

        return false;
    }

}
