/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;

@Singleton
public class BuiltinLibraryUtils {

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

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

        throw new IllegalStateException("Argument could not be resolved!");
    }

}
