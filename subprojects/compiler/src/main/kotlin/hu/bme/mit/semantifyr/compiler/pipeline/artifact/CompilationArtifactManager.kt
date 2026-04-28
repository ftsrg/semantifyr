/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

@CompilationScoped
class CompilationArtifactManager @Inject constructor(
    val serializer: ISerializer,
    val artifactManager: ArtifactManager,
    val artifactConfig: ArtifactConfig,
    private val request: CompilationRequest,
) {

    private val inlinedOxsts
        get() = request.inlinedOxsts

    private var stepId = 0

    fun commitStep(pass: CompilationPass) {
        if (!artifactConfig.enabledCompilationSteps.shouldEmit(pass)) {
            return
        }

        artifactManager.withFile(ArtifactKind.CompilationStep) {
            it.mkdirs()
            val id = stepId++.toString().padStart(6, '0')
            serializeInto(inlinedOxsts, it.resolve("${id}_${pass.name.lowercase()}.oxsts"))
        }
    }

    fun commitInstantiated() {
        commitPhase(ArtifactKind.InstantiatedModel)
    }

    fun commitInlined() {
        commitPhase(ArtifactKind.InlinedModel)
    }

    fun commitFlattened() {
        commitPhase(ArtifactKind.FlattenedModel)
    }

    private fun commitPhase(kind: ArtifactKind) {
        artifactManager.withFile(kind) {
            serializeInto(inlinedOxsts, it)
        }
    }

    private fun serializeInto(
        inlinedOxsts: InlinedOxsts,
        modelFile: File,
    ) {
        serializer.serialize(inlinedOxsts, modelFile.bufferedWriter(), SaveOptions.defaultOptions())
    }

}
