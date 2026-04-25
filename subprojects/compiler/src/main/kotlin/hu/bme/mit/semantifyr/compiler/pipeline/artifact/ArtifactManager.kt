/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import org.eclipse.emf.common.util.URI
import java.io.File

private const val PIPELINE = "pipeline"
private const val OUTPUT_MODEL = "inlined.oxsts"
private const val INSTANTIATED_MODEL = "$PIPELINE/instantiated.oxsts"
private const val INLINED_MODEL = "$PIPELINE/inlined.oxsts"
private const val FLATTENED_MODEL = "$PIPELINE/flattened.oxsts"
private const val COMPILATION_STEPS = "$PIPELINE/steps"
private const val WITNESS = "witness.oxsts"
private const val TRACE = "trace.json"
private const val MAPPING = "mapping.json"
private const val REPORT = "report.json"

class ArtifactManager @Inject constructor(
    private val config: ArtifactConfig,
) {

    fun pathOf(artifactKind: ArtifactKind): String {
        return when (artifactKind) {
            ArtifactKind.OutputModel -> OUTPUT_MODEL
            ArtifactKind.InstantiatedModel -> INSTANTIATED_MODEL
            ArtifactKind.InlinedModel -> INLINED_MODEL
            ArtifactKind.FlattenedModel -> FLATTENED_MODEL
            ArtifactKind.CompilationStep -> COMPILATION_STEPS
            ArtifactKind.Witness -> WITNESS
            ArtifactKind.Trace -> TRACE
            ArtifactKind.Mapping -> MAPPING
            ArtifactKind.Report -> REPORT
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
