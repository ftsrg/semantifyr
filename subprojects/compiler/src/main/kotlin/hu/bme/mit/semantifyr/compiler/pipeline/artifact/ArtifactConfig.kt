/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import java.nio.file.Path

enum class ArtifactKind {
    OutputModel,
    InlinedModel,
    InflatedModel,
    DeflatedModel,
    CompilationStep,
    Witness,
    Mapping,
    Trace,
    Report,
}

enum class CompilationPass {
    Inflation,
    ExpressionCallInlining,
    OperationCallInlining,
    ConstantFolding,
    ExpressionSimplification,
    RedundantOperationRemoval,
    OperationFlattening,
    AssumptionPropagation,
    DeadCodeRemoval,
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

data class ArtifactConfig(
    val outputDirectory: Path,
    val enabled: Set<ArtifactKind> = ReportOnlyArtifacts,
    val enabledCompilationSteps: CompilationStepsConfig = CompilationStepsConfig.Off,
) {
    fun isEnabled(kind: ArtifactKind): Boolean {
        return kind in enabled
    }

    companion object {
        private val ReportOnlyArtifacts = ArtifactKind.entries.toSet() - ArtifactKind.CompilationStep

        /**
         * Disables artifact emission.
         */
        fun none(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = emptySet()
        )

        /**
         * Emits every non-debug artifact (models, witness, trace, mapping, report).
         */
        fun all(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = ReportOnlyArtifacts,
        )

        /**
         * Emits every artifact including per-pass step dumps.
         */
        fun debug(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = ArtifactKind.entries.toSet(),
            enabledCompilationSteps = CompilationStepsConfig.All,
        )
    }
}
