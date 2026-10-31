/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import kotlinx.serialization.Serializable
import kotlin.time.Duration

interface Optimizer<T> {
    fun optimize(input: T): OptimizationStats
}

@Serializable
data class PassStats(
    val name: String,
    val duration: Duration = Duration.ZERO,
    val runs: Int = 0,
    val firings: Int = 0,
)

@Serializable
data class AnalysisStats(
    val name: String,
    val duration: Duration = Duration.ZERO,
    val computations: Int = 0,
)

@Serializable
data class OptimizationStats(
    val duration: Duration = Duration.ZERO,
    val passes: List<PassStats> = emptyList(),
    val analyses: List<AnalysisStats> = emptyList(),
) {
    val changed: Boolean
        get() = passes.any { it.firings > 0 }

    operator fun plus(other: OptimizationStats): OptimizationStats {
        return OptimizationStats(
            duration = duration + other.duration,
            passes = (passes + other.passes).groupBy { it.name }.map { (name, stats) ->
                PassStats(
                    name = name,
                    duration = stats.fold(Duration.ZERO) { total, stat -> total + stat.duration },
                    runs = stats.sumOf { it.runs },
                    firings = stats.sumOf { it.firings },
                )
            },
            analyses = (analyses + other.analyses).groupBy { it.name }.map { (name, stats) ->
                AnalysisStats(
                    name = name,
                    duration = stats.fold(Duration.ZERO) { total, stat -> total + stat.duration },
                    computations = stats.sumOf { it.computations },
                )
            },
        )
    }
}
