/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

@Singleton
public class BuiltinAnnotationHandler {

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

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

    public VerificationCaseExpectedResult getExpectedResults(ClassDeclaration classDeclaration) {
        var annotationDeclaration = builtinSymbolResolver.verificationCaseAnnotation(classDeclaration);
        var annotation = OxstsUtils.getAnnotation(classDeclaration, annotationDeclaration);
        var expectedResultsParameter = builtinSymbolResolver.verificationCaseAnnotationExpectedResults(classDeclaration);

        var value = OxstsUtils.getAnnotationValue(annotation, expectedResultsParameter);

        if (value instanceof ElementReference elementReference) {
            if ("SAFE".equals(elementReference.getElement().getName())) {
                return VerificationCaseExpectedResult.SAFE;
            }
            if ("UNSAFE".equals(elementReference.getElement().getName())) {
                return VerificationCaseExpectedResult.UNSAFE;
            }
            if ("UNKNOWN".equals(elementReference.getElement().getName())) {
                return VerificationCaseExpectedResult.UNKNOWN;
            }
        }

        return null;
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

}
