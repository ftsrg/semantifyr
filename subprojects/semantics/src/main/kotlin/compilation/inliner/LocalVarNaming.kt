/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation.inliner

import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation

object LocalVarNaming {
    const val LOCAL_VAR_SUFFIX = $$$$"$$$local"

    fun inlinedName(localVar: LocalVarDeclarationOperation, index: Int): String {
        return "${localVar.name}$LOCAL_VAR_SUFFIX$index"
    }

    fun isLocalVar(xstsName: String): Boolean {
        return xstsName.contains(LOCAL_VAR_SUFFIX)
    }
}
