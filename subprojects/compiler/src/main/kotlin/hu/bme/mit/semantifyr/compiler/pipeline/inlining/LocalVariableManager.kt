/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation

@CompilationScoped
class LocalVariableManager {

    private var nextIndex = 0

    // keyed by the inlined name, which copies of an inlined body share
    private val instanceDomains = mutableMapOf<String, Set<Instance>>()

    fun allocateInlinedName(localVar: LocalVarDeclarationOperation): String {
        return LocalVarNames.inlinedName(localVar, nextIndex++)
    }

    fun registerInstanceDomain(localVar: LocalVarDeclarationOperation, instances: Set<Instance>) {
        instanceDomains[localVar.name] = instances
    }

    fun instanceDomainOf(localVar: LocalVarDeclarationOperation): Set<Instance>? {
        return instanceDomains[localVar.name]
    }

}
