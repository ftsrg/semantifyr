/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.FileExtensionProvider
import java.io.File
import java.nio.file.Path

private const val PIPELINE = "pipeline"
private const val COMPILATION_STEPS = "$PIPELINE/steps"
private const val MAPPING = "mapping.json"

class ArtifactManager @Inject constructor(
    private val config: ArtifactConfig,
    private val request: CompilationRequest,
    fileExtensionProvider: FileExtensionProvider,
) {

    private val modelExtension = fileExtensionProvider.primaryFileExtension

    fun pathOf(artifactKind: ArtifactKind): String {
        return when (artifactKind) {
            ArtifactKind.InstantiatedModel -> "$PIPELINE/instantiated.$modelExtension"
            ArtifactKind.InlinedModel -> "$PIPELINE/inlined.$modelExtension"
            ArtifactKind.FlattenedModel -> "$PIPELINE/flattened.$modelExtension"
            ArtifactKind.CompilationStep -> COMPILATION_STEPS
            ArtifactKind.Mapping -> MAPPING
        }
    }

    private val basePath: File = request.outputDirectory.toFile()

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

    fun pathOrNull(kind: ArtifactKind): Path? {
        if (!config.isEnabled(kind)) {
            return null
        }
        return resolve(kind).toPath()
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
