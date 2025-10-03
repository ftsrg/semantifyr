/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

class ArtifactSaver {

    lateinit var basePath: File

    lateinit var inlinedOxsts: InlinedOxsts

    @Inject
    private lateinit var serializer: ISerializer

    private var id = 0

    fun commitModelState() {
        val modelFile = basePath.resolve("state${id++}.oxsts")
        modelFile.parentFile.mkdirs()

        val fileWriter = modelFile.writer()
        serializer.serialize(inlinedOxsts, fileWriter, SaveOptions.defaultOptions())
    }

}

@Singleton
class CompilationArtifactSaver {

    @Inject
    protected lateinit var artifactSaverProvider: Provider<ArtifactSaver>

    private var artifactSaver: ArtifactSaver? = null

    fun initArtifactManager(inlinedOxsts: InlinedOxsts): ArtifactSaver {
        artifactSaver = artifactSaverProvider.get()
        artifactSaver!!.inlinedOxsts = inlinedOxsts
        artifactSaver!!.basePath = File(inlinedOxsts.eResource().uri.toFileString() + ".artifacts.d")
        artifactSaver!!.basePath.deleteRecursively()
        return artifactSaver!!
    }

    fun finalizeArtifactManager(inlinedOxsts: InlinedOxsts) {
        artifactSaver = null
        inlinedOxsts.eResource().save(emptyMap<Any, Any>())
    }

    fun commitModelState() {
        artifactSaver?.commitModelState()
    }

}
