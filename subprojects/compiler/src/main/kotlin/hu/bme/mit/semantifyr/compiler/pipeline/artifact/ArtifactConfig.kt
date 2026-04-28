/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import kotlinx.serialization.Serializable

@Serializable
enum class ArtifactKind {
    OutputModel,
    InstantiatedModel,
    InlinedModel,
    FlattenedModel,
    CompilationStep,
    Witness,
    Mapping,
    Trace,
    Report,
}

@Serializable
enum class CompilationPass {
    ExpressionCallInlining,
    OperationCallInlining,
    ConstantFolding,
    ConstantVariableSubstitution,
    CopyPropagation,
    ExpressionSimplification,
    RedundantOperationRemoval,
    DeadStoreElimination,
    OperationFlattening,
    AssumptionPropagation,
    DeadCodeRemoval,
    UnusedVariableElimination,
    Flattening,
}

@Serializable
sealed interface CompilationStepsConfig {
    fun shouldEmit(pass: CompilationPass): Boolean

    @Serializable
    object Off : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return false
        }
    }

    @Serializable
    object All : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return true
        }
    }

    @Serializable
    data class Selected(
        val passes: Set<CompilationPass>,
    ) : CompilationStepsConfig {
        override fun shouldEmit(pass: CompilationPass): Boolean {
            return pass in passes
        }
    }
}

@Serializable
data class ArtifactConfig(
    val enabled: Set<ArtifactKind> = ReportOnlyArtifacts,
    val enabledCompilationSteps: CompilationStepsConfig = CompilationStepsConfig.Off,
) {
    fun isEnabled(kind: ArtifactKind): Boolean {
        return kind in enabled
    }

    companion object {
        private val ReportOnlyArtifacts = ArtifactKind.entries.toSet() - ArtifactKind.CompilationStep

        @JvmField
        val NONE: ArtifactConfig = ArtifactConfig(enabled = emptySet())

        /**
         * Emits every non-debug artifact (models, witness, trace, mapping, report).
         */
        @JvmField
        val ALL: ArtifactConfig = ArtifactConfig(enabled = ReportOnlyArtifacts)

        /**
         * Emits every artifact including per-pass step dumps.
         */
        @JvmField
        val DEBUG: ArtifactConfig = ArtifactConfig(
            enabled = ArtifactKind.entries.toSet(),
            enabledCompilationSteps = CompilationStepsConfig.All,
        )
    }
}
