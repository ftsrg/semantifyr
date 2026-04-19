/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext

class OxstsInstantiator @Inject constructor(
    private val instanceTreeCreator: InstanceTreeCreator,
) {

    fun instantiate(compilationContext: CompilationContext): InstantiatedCompilationContext {
        val instanceTree = instanceTreeCreator.create(compilationContext.inlinedOxsts)
        return compilationContext.instantiated(instanceTree)
    }

}
