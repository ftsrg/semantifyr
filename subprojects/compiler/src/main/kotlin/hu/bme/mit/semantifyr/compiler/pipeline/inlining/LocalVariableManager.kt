/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation

@CompilationScoped
class LocalVariableManager {

    private var nextIndex = 0

    fun allocateInlinedName(localVar: LocalVarDeclarationOperation): String {
        return LocalVarNames.inlinedName(localVar, nextIndex++)
    }

}
