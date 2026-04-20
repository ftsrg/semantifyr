/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

@Singleton
class CompilationArtifactManager @Inject constructor(
    val serializer: ISerializer,
    val artifactManager: ArtifactManager,
    val artifactConfig: ArtifactConfig
) {

    private lateinit var inlinedOxsts: InlinedOxsts

    private var stepId = 0

    fun setTarget(inlinedOxsts: InlinedOxsts) {
        check(!::inlinedOxsts.isInitialized) {
            "CompilationArtifactManager.setTarget() was already called on this instance. Each compilation must use a fresh manager (the verifier / compiler builder creates per-compilation injectors)."
        }
        this.inlinedOxsts = inlinedOxsts
    }

    fun commitStep(pass: CompilationPass) {
        if (!artifactConfig.enabledCompilationSteps.shouldEmit(pass)) {
            return
        }

        check(::inlinedOxsts.isInitialized) {
            "CompilationArtifactManager.commitStep($pass) called before setTarget(). The orchestrator must call setTarget() before any pass runs."
        }

        artifactManager.withFile(ArtifactKind.CompilationStep) { stepsDir ->
            stepsDir.mkdirs()
            val id = stepId++.toString().padStart(6, '0')
            serializeInto(inlinedOxsts, stepsDir.resolve("${id}_${pass.name.lowercase()}.oxsts"))
        }
    }

    fun commitInstantiated() {
        check(::inlinedOxsts.isInitialized) {
            "CompilationArtifactManager.commitInstantiated() called before setTarget()."
        }
        artifactManager.withFile(ArtifactKind.InflatedModel) { serializeInto(inlinedOxsts, it) }
    }

    fun commitInlined() {
        check(::inlinedOxsts.isInitialized) {
            "CompilationArtifactManager.commitInlined() called before setTarget()."
        }
        artifactManager.withFile(ArtifactKind.InlinedModel) { serializeInto(inlinedOxsts, it) }
    }

    fun commitFlattened() {
        check(::inlinedOxsts.isInitialized) {
            "CompilationArtifactManager.commitFlattened() called before setTarget()."
        }
        artifactManager.withFile(ArtifactKind.DeflatedModel) { serializeInto(inlinedOxsts, it) }
    }

    private fun serializeInto(inlinedOxsts: InlinedOxsts, modelFile: File) {
        serializer.serialize(inlinedOxsts, modelFile.bufferedWriter(), SaveOptions.defaultOptions())
    }

}
