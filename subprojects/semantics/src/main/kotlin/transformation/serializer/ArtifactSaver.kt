/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

@CompilationScoped
class CompilationStateManager {

    private lateinit var basePath: File

    private lateinit var inlinedOxsts: InlinedOxsts
    private lateinit var progressContext: ProgressContext

    private var id = 0

    @Inject
    private lateinit var serializer: ISerializer

    fun initArtifactManager(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext) {
        this.inlinedOxsts = inlinedOxsts
        this.progressContext = progressContext
        basePath = File("/tmp/semantifyr-output")
        basePath.deleteRecursively()
    }

    fun finalizeArtifactManager(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.eResource().save(emptyMap<Any, Any>())
    }

    fun commitModelState() {
        progressContext.checkIsCancelled()

//        val modelFile = basePath.resolve("state${id++}.oxsts")
//        modelFile.parentFile.mkdirs()
//
//        val fileWriter = modelFile.writer()
//        serializer.serialize(inlinedOxsts, fileWriter, SaveOptions.defaultOptions())
    }

}
