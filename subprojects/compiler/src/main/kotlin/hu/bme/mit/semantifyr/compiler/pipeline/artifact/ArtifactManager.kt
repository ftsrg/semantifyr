/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import org.eclipse.emf.common.util.URI
import java.io.File

private const val pipeline = "pipeline"
private const val outputModel = "inlined.oxsts"
private const val instantiatedModel = "$pipeline/instantiated.oxsts"
private const val inlinedModel = "$pipeline/inlined.oxsts"
private const val flattenedModel = "$pipeline/flattened.oxsts"
private const val compilationSteps = "$pipeline/steps"
private const val witness = "witness.oxsts"
private const val trace = "trace.json"
private const val mapping = "mapping.json"
private const val report = "report.json"

class ArtifactManager @Inject constructor(
    private val config: ArtifactConfig,
) {

    fun pathOf(artifactKind: ArtifactKind): String {
        return when (artifactKind) {
            ArtifactKind.OutputModel -> outputModel
            ArtifactKind.InstantiatedModel -> instantiatedModel
            ArtifactKind.InlinedModel -> inlinedModel
            ArtifactKind.FlattenedModel -> flattenedModel
            ArtifactKind.CompilationStep -> compilationSteps
            ArtifactKind.Witness -> witness
            ArtifactKind.Trace -> trace
            ArtifactKind.Mapping -> mapping
            ArtifactKind.Report -> report
        }
    }

    private val basePath: File = config.outputDirectory.toFile()

    private fun resolve(relativePath: String): File {
        return basePath.resolve(relativePath)
    }

    private fun resolve(kind: ArtifactKind): File {
        return resolve(pathOf(kind))
    }

    fun resolveUri(relativePath: String): URI {
        return URI.createFileURI(resolve(relativePath).absolutePath)
    }

    fun resolveUri(kind: ArtifactKind): URI {
        return resolveUri(pathOf(kind))
    }

    fun withFile(kind: ArtifactKind, block: (File) -> Unit) {
        if (!config.isEnabled(kind)) {
            return
        }
        val path = pathOf(kind)
        val file = resolve(path)
        file.parentFile?.mkdirs()
        block(file)
    }

}
