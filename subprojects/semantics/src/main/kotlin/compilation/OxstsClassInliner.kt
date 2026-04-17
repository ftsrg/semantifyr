/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactManager
import hu.bme.mit.semantifyr.semantics.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.semantics.compilation.inliner.OxstsCallInliner
import hu.bme.mit.semantifyr.semantics.compilation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped

@CompilationScoped
class OxstsClassInliner @Inject constructor(
    private val inlinedOxstsModelCreator: InlinedOxstsModelCreator,
    private val oxstsInflator: OxstsInflator,
    private val oxstsCallInliner: OxstsCallInliner,
    private val oxstsInliningManager: CompilationArtifactManager,
) {

    fun inline(classDeclaration: ClassDeclaration): InlinedOxsts {
        val inlinedOxsts = inlinedOxstsModelCreator.createInlinedOxsts(classDeclaration)
        return inlinePipeline(inlinedOxsts)
    }

    fun inline(witness: InlinedOxsts): InlinedOxsts {
        return inlinePipeline(witness)
    }

    private fun inlinePipeline(inlinedOxsts: InlinedOxsts): InlinedOxsts {
        oxstsInliningManager.initialize(inlinedOxsts)

        oxstsInflator.inflateInstanceModel(inlinedOxsts)

        oxstsInliningManager.commitInflated()

        oxstsCallInliner.inlineCalls(inlinedOxsts)

        oxstsInliningManager.commitInlined()

        oxstsInflator.deflateInstanceModel(inlinedOxsts)

        oxstsInliningManager.commitDeflated()

        return inlinedOxsts
    }

}
