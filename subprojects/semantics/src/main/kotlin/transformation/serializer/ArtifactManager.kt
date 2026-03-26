/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import org.eclipse.emf.common.util.URI
import java.io.File

@CompilationScoped
class ArtifactManager {

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
