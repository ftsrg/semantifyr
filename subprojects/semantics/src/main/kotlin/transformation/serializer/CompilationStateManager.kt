/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

@CompilationScoped
class CompilationStateManager {

    private lateinit var inlinedOxsts: InlinedOxsts
    private lateinit var progressContext: ProgressContext

    private var id = 0

    @Inject
    private lateinit var serializer: ISerializer

    @Inject
    private lateinit var inlinedArtifactManager: InlinedArtifactManager

    var isSerializeSteps = false

    fun initialize(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext) {
        this.inlinedOxsts = inlinedOxsts
        this.progressContext = progressContext

        commitModelState()
    }

    fun finalize(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.eResource().save(emptyMap<Any, Any>())
    }

    fun commitModelState() {
        progressContext.checkIsCancelled()

        if (isSerializeSteps) {
            serializeStep()
        }
    }

    fun commitInflated() {
        serializeInto(inlinedArtifactManager.inflatedOxstsFile)
    }

    fun commitInlined() {
        serializeInto(inlinedArtifactManager.inlinedOxstsFile)
    }

    fun commitDeflated() {
        serializeInto(inlinedArtifactManager.deflatedOxstsFile)
    }

    fun serializeStep() {
        serializeInto(inlinedArtifactManager.stepOxstsFile(id++))
    }

    fun serializeInto(modelFile: File) {
        modelFile.parentFile.mkdirs()
        serializer.serialize(inlinedOxsts, modelFile.bufferedWriter(), SaveOptions.defaultOptions())
    }

}
