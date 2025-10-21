/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OxstsCallInliner
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager

@CompilationScoped
class OxstsClassInliner {

    @Inject
    private lateinit var inlinedOxstsModelManager: InlinedOxstsModelManager

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    @Inject
    private lateinit var oxstsCallInliner: OxstsCallInliner

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    fun transform(progressContext: ProgressContext, classDeclaration: ClassDeclaration) {
        val inlinedOxsts = inlinedOxstsModelManager.createInlinedOxsts(classDeclaration)

        compilationStateManager.initArtifactManager(inlinedOxsts, progressContext)

        progressContext.reportProgress("Instantiating model", 10)

        oxstsInflator.inflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Inlining calls", 20)

        oxstsCallInliner.inlineCalls(inlinedOxsts)

        progressContext.reportProgress("Deflating instances and structure", 60)

        oxstsInflator.deflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Serializing final model", 90)

        compilationStateManager.finalizeArtifactManager(inlinedOxsts)
    }

}
