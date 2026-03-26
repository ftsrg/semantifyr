/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import java.io.File

@CompilationScoped
class InlinedArtifactManager {

    @Inject
    private lateinit var artifactManager: ArtifactManager

    private val directory: File by lazy {
        artifactManager.resolve("inlined").also { it.mkdirs() }
    }

    val inflatedOxstsFile: File
        get() = directory.resolve("inflated.oxsts")

    val inlinedOxstsFile: File
        get() = directory.resolve("inlined.oxsts")

    val deflatedOxstsFile: File
        get() = directory.resolve("deflated.oxsts")

    fun stepOxstsFile(id: Int): File {
        return directory.resolve("step$id.oxsts")
    }

}
