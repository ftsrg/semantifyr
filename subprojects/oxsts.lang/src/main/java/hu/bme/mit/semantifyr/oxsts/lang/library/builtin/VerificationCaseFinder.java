/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.stream.Stream;

public class VerificationCaseFinder {

    @Inject
    private BuiltinAnnotationHandler builtinAnnotationHandler;

    public Stream<ClassDeclaration> streamTestCases(OxstsModelPackage oxstsModelPackage) {
        return oxstsModelPackage.getDeclarations().stream()
                .filter(declaration -> declaration instanceof ClassDeclaration)
                .map(declaration -> (ClassDeclaration) declaration)
                .filter(classDeclaration -> builtinAnnotationHandler.isVerificationCase(classDeclaration));
    }
}
