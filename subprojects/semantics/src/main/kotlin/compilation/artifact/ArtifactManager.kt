/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped
import org.eclipse.emf.common.util.URI
import java.io.File

@CompilationScoped
class ArtifactManager @Inject constructor(
    private val config: ArtifactConfig,
) {

    private val basePath: File = config.outputDirectory.toFile()

    private fun resolve(relativePath: String): File {
        return basePath.resolve(relativePath)
    }

    private fun resolve(kind: ArtifactKind): File {
        return resolve(ArtifactKindFiles.pathOf(kind))
    }

    fun resolveUri(relativePath: String): URI {
        return URI.createFileURI(resolve(relativePath).absolutePath)
    }

    fun resolveUri(kind: ArtifactKind): URI {
        return resolveUri(ArtifactKindFiles.pathOf(kind))
    }

    fun withFile(kind: ArtifactKind, block: (File) -> Unit) {
        if (!config.isEnabled(kind)) {
            return
        }
        val path = ArtifactKindFiles.pathOf(kind)
        val file = resolve(path)
        file.parentFile?.mkdirs()
        block(file)
    }

}
