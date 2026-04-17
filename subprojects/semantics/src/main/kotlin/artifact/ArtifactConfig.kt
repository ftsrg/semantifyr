/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.artifact

import java.nio.file.Path

enum class ArtifactKind {
    InlinedModel,
    InflatedModel,
    DeflatedModel,
    CompilationStep,
    Witness,
    Mapping,
    Trace,
    Report,
}

data class ArtifactConfig(
    val outputDirectory: Path,
    val enabled: Set<ArtifactKind> = ArtifactKind.entries.toSet(),
    val compilationSteps: CompilationStepsConfig = CompilationStepsConfig.Off,
) {
    fun isEnabled(kind: ArtifactKind): Boolean {
        return kind in enabled
    }

    companion object {
        fun none(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = emptySet()
        )

        fun reportOnly(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = setOf(
                ArtifactKind.Witness,
                ArtifactKind.Trace,
                ArtifactKind.Report,
            )
        )

        fun all(outputDirectory: Path) = ArtifactConfig(
            outputDirectory = outputDirectory,
            enabled = ArtifactKind.entries.toSet(),
            compilationSteps = CompilationStepsConfig.All,
        )
    }
}

object ArtifactKindFiles {

    private const val inlining = "inlining"

    private const val inlinedModel = "$inlining/inlined.oxsts"
    private const val inflatedModel = "$inlining/inflated.oxsts"
    private const val deflatedModel = "$inlining/deflated.oxsts"
    private const val compilationSteps = "$inlining/steps"
    private const val witness = "witness.oxsts"
    private const val trace = "trace.json"
    private const val mapping = "mapping.json"
    private const val report = "report.json"

    fun pathOf(artifactKind: ArtifactKind): String {
        return when (artifactKind) {
            ArtifactKind.InlinedModel -> inlinedModel
            ArtifactKind.InflatedModel -> inflatedModel
            ArtifactKind.DeflatedModel -> deflatedModel
            ArtifactKind.CompilationStep -> compilationSteps
            ArtifactKind.Witness -> witness
            ArtifactKind.Trace -> trace
            ArtifactKind.Mapping -> mapping
            ArtifactKind.Report -> report
        }
    }

}
