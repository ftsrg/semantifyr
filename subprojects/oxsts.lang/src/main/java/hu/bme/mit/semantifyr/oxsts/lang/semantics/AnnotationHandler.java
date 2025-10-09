/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;

@Singleton
public class AnnotationHandler {

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public boolean isControlVariable(VariableDeclaration variableDeclaration) {
        var controlAnnotation = builtinSymbolResolver.controlAnnotation(variableDeclaration);

        return OxstsUtils.isAnnotatedWith(variableDeclaration, controlAnnotation);
    }

    public boolean isVerificationCase(ClassDeclaration classDeclaration) {
        var controlAnnotation = builtinSymbolResolver.verificationCaseAnnotation(classDeclaration);

        return OxstsUtils.isAnnotatedWith(classDeclaration, controlAnnotation);
    }

}
