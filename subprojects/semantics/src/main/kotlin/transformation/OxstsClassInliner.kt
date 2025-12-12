/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OxstsCallInliner
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager

@CompilationScoped
class OxstsClassInliner {

    @Inject
    private lateinit var inlinedOxstsModelCreator: InlinedOxstsModelCreator

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    @Inject
    private lateinit var oxstsCallInliner: OxstsCallInliner

    @Inject
    private lateinit var oxstsInliningManager: CompilationStateManager

    fun inline(progressContext: ProgressContext, classDeclaration: ClassDeclaration): InlinedOxsts {
        val inlinedOxsts = inlinedOxstsModelCreator.createInlinedOxsts(classDeclaration)

        oxstsInliningManager.initialize(inlinedOxsts, progressContext)

        oxstsInflator.inflateInstanceModel(inlinedOxsts)

        oxstsInliningManager.commitInflated()

        oxstsCallInliner.inlineCalls(inlinedOxsts)

        oxstsInliningManager.commitInlined()

        oxstsInflator.deflateInstanceModel(inlinedOxsts)

        oxstsInliningManager.commitDeflated()

        oxstsInliningManager.finalize(inlinedOxsts)

        return inlinedOxsts
    }

}
