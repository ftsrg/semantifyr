/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisStats
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassStats
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class VerificationResultDtoTest {

    @Test
    fun `the java metrics carry the optimizer stats and the model sizes`() {
        val metrics = VerificationMetrics(
            verifier = VerifierMetrics(
                compilation = CompilationMetrics(
                    flatteningOptimization = OptimizationStats(
                        duration = 5.milliseconds,
                        passes = listOf(PassStats("CopyPropagationPass", 3.milliseconds, 2, 1)),
                        analyses = listOf(AnalysisStats("ReachingDefinitionsAnalysis", 1.milliseconds, 2)),
                    ),
                    flattenedSize = ModelSize(1, 5, 9),
                ),
            ),
        )

        val compilation = metrics.toJavaDto().verifier.compilation

        assertThat(compilation.flatteningOptimization.duration).isEqualTo(Duration.ofMillis(5))
        assertThat(compilation.flatteningOptimization.passes).containsExactly(PassStatsDto("CopyPropagationPass", Duration.ofMillis(3), 2, 1))
        assertThat(compilation.flatteningOptimization.analyses).containsExactly(AnalysisStatsDto("ReachingDefinitionsAnalysis", Duration.ofMillis(1), 2))
        assertThat(compilation.flattenedSize).isEqualTo(ModelSize(1, 5, 9))
    }
}
