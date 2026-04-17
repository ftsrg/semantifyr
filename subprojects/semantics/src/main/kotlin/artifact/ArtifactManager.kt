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
    private val config: ArtifactConfig
) {

    private lateinit var basePath: File

    fun initialize(basePath: File) {
        this.basePath = basePath
        basePath.deleteRecursively()
        basePath.mkdirs()
    }

    fun resolve(relativePath: String): File {
        return basePath.resolve(relativePath)
    }

    fun resolveUri(relativePath: String): URI {
        return URI.createFileURI(resolve(relativePath).absolutePath)
    }

}
