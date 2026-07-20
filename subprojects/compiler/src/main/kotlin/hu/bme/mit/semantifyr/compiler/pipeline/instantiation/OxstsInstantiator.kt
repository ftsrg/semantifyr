/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext

class OxstsInstantiator @Inject constructor(
    private val instanceTreeCreator: InstanceTreeCreator,
) {

    fun instantiate(created: CreatedCompilationContext): InstantiatedCompilationContext {
        val instanceTree = instanceTreeCreator.create(created.inlinedOxsts)
        return created.instantiated(instanceTree)
    }

}
