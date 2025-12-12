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

    var isSerializeSteps = false

    fun initialize(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext) {
        this.inlinedOxsts = inlinedOxsts
        this.progressContext = progressContext
        basePath = File(inlinedOxsts.eResource().uri.toFileString().replace(".oxsts", "${File.separator}steps"))
        basePath.deleteRecursively()

        commitModelState()
    }

    fun finalize(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.eResource().save(emptyMap<Any, Any>())
    }

    fun commitModelState() {
        progressContext.checkIsCancelled()

        if (isSerializeSteps) {
            serializeStep()
        }
    }

    fun commitInflated() {
        val modelFile = basePath.resolve("inlfated.oxsts")
        serializeInto(modelFile)
    }

    fun commitInlined() {
        val modelFile = basePath.resolve("inlined.oxsts")
        serializeInto(modelFile)
    }

    fun commitDeflated() {
        val modelFile = basePath.resolve("deflated.oxsts")
        serializeInto(modelFile)
    }

    fun serializeStep() {
        val modelFile = basePath.resolve("step${id++}.oxsts")
        serializeInto(modelFile)
    }

    fun serializeInto(modelFile: File) {
        modelFile.parentFile.mkdirs()
        serializer.serialize(inlinedOxsts, modelFile.bufferedWriter(), SaveOptions.defaultOptions())
    }

}
