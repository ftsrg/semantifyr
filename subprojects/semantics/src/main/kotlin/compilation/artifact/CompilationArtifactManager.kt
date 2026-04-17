/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.File

enum class CompilationPass {
    Inflation,
    ExpressionCallInlining,
    OperationCallInlining,
    ConstantFolding,
    ExpressionSimplification,
    ComparisonSimplification,
    RedundantOperationRemoval,
    OperationFlattening,
    AssumptionPropagation,
    DeadBranchElimination,
    UnusedVariableElimination,
    Deflation,
}

sealed interface CompilationStepsConfig {
    fun shouldEmit(pass: CompilationPass): Boolean

    object Off : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return false
        }
    }

    object All : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return true
        }
    }

    data class Selected(val passes: Set<CompilationPass>) : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return pass in passes
        }
    }
}

@CompilationScoped
class CompilationArtifactManager @Inject constructor(
    val serializer: ISerializer,
    val artifactManager: ArtifactManager,
    val compilationStepsConfig: CompilationStepsConfig,
) {

    private lateinit var inlinedOxsts: InlinedOxsts

    private var stepId = 0

    fun initialize(inlinedOxsts: InlinedOxsts) {
        this.inlinedOxsts = inlinedOxsts
    }

    fun commitStep(pass: CompilationPass) {
        if (!compilationStepsConfig.shouldEmit(pass)) {
            return
        }

        artifactManager.withFile(ArtifactKind.CompilationStep) {
            serializeInto(it.resolve("${pass.name.lowercase()}_${stepId++}.oxsts"))
        }
    }

    fun commitInflated() {
        artifactManager.withFile(ArtifactKind.InflatedModel) { serializeInto(it) }
    }

    fun commitInlined() {
        artifactManager.withFile(ArtifactKind.InlinedModel) { serializeInto(it) }
    }

    fun commitDeflated() {
        artifactManager.withFile(ArtifactKind.DeflatedModel) { serializeInto(it) }
    }

    private fun serializeInto(modelFile: File) {
        serializer.serialize(inlinedOxsts, modelFile.bufferedWriter(), SaveOptions.defaultOptions())
    }

}
