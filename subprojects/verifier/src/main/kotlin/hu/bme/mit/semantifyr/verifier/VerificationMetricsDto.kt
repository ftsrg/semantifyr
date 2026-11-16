/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import java.time.Duration
import kotlin.time.toJavaDuration

data class BackendMetricsDto(
    val preparationDuration: Duration,
    val verificationDuration: Duration,
    val backAnnotationDuration: Duration,
) {
    companion object {
        fun fromMetrics(metrics: BackendMetrics): BackendMetricsDto {
            return BackendMetricsDto(
                preparationDuration = metrics.preparationDuration.toJavaDuration(),
                verificationDuration = metrics.verificationDuration.toJavaDuration(),
                backAnnotationDuration = metrics.backAnnotationDuration.toJavaDuration(),
            )
        }
    }
}

data class PassStatisticsDto(
    val name: String,
    val duration: Duration,
    val runs: Int,
    val firings: Int,
)

data class AnalysisStatisticsDto(
    val name: String,
    val duration: Duration,
    val computations: Int,
)

data class OptimizationStatisticsDto(
    val duration: Duration,
    val passes: List<PassStatisticsDto>,
    val analyses: List<AnalysisStatisticsDto>,
) {
    companion object {
        fun fromStatistics(statistics: OptimizationStats): OptimizationStatisticsDto {
            return OptimizationStatisticsDto(
                duration = statistics.duration.toJavaDuration(),
                passes = statistics.passes.map {
                    PassStatisticsDto(it.name, it.duration.toJavaDuration(), it.runs, it.firings)
                },
                analyses = statistics.analyses.map {
                    AnalysisStatisticsDto(it.name, it.duration.toJavaDuration(), it.computations)
                },
            )
        }
    }
}

data class CompilationMetricsDto(
    val createDuration: Duration,
    val instantiationDuration: Duration,
    val inliningDuration: Duration,
    val flatteningDuration: Duration,
    val inliningOptimization: OptimizationStatisticsDto,
    val flatteningOptimization: OptimizationStatisticsDto,
    val inputSize: ModelSize,
    val inlinedSize: ModelSize,
    val flattenedSize: ModelSize,
) {
    companion object {
        fun fromMetrics(metrics: CompilationMetrics): CompilationMetricsDto {
            return CompilationMetricsDto(
                createDuration = metrics.createDuration.toJavaDuration(),
                instantiationDuration = metrics.instantiationDuration.toJavaDuration(),
                inliningDuration = metrics.inliningDuration.toJavaDuration(),
                flatteningDuration = metrics.flatteningDuration.toJavaDuration(),
                inliningOptimization = OptimizationStatisticsDto.fromStatistics(metrics.inliningOptimization),
                flatteningOptimization = OptimizationStatisticsDto.fromStatistics(metrics.flatteningOptimization),
                inputSize = metrics.inputSize,
                inlinedSize = metrics.inlinedSize,
                flattenedSize = metrics.flattenedSize,
            )
        }
    }
}

data class VerifierMetricsDto(
    val compilationDuration: Duration,
    val compilation: CompilationMetricsDto,
    val backAnnotationDuration: Duration,
    val portfolioDuration: Duration,
) {
    companion object {
        fun fromMetrics(metrics: VerifierMetrics): VerifierMetricsDto {
            return VerifierMetricsDto(
                compilationDuration = metrics.compilationDuration.toJavaDuration(),
                compilation = CompilationMetricsDto.fromMetrics(metrics.compilation),
                backAnnotationDuration = metrics.backAnnotationDuration.toJavaDuration(),
                portfolioDuration = metrics.portfolioDuration.toJavaDuration(),
            )
        }
    }
}

data class VerificationMetricsDto(
    val totalDuration: Duration,
    val backend: BackendMetricsDto?,
    val verifier: VerifierMetricsDto,
) {
    companion object {
        fun fromMetrics(result: VerificationMetrics): VerificationMetricsDto {
            return VerificationMetricsDto(
                totalDuration = result.totalDuration.toJavaDuration(),
                backend = result.backend?.let { BackendMetricsDto.fromMetrics(it) },
                verifier = VerifierMetricsDto.fromMetrics(result.verifier),
            )
        }
    }
}
