/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class BenchmarkOptionsTest {

    @Test
    fun `positional arguments, flags and values are told apart`() {
        val options = BenchmarkOptions.parse(listOf("run", "crossroads", "--fresh", "--output", "out"))

        assertThat(options.command).isEqualTo("run")
        assertThat(options.argument(0, "campaign name")).isEqualTo("crossroads")
        assertThat(options.flag("fresh")).isTrue()
        assertThat(options.path("output", "results")).isEqualTo(Path.of("out"))
        assertThat(options.path("reference", "reference")).isEqualTo(Path.of("reference"))
    }

    @Test
    fun `analyze takes the results and the output directory`() {
        val options = BenchmarkOptions.parse(listOf("analyze", "results/crossroads", "analysis"))

        assertThat(options.argument(0, "results directory")).isEqualTo("results/crossroads")
        assertThat(options.argument(1, "output directory")).isEqualTo("analysis")
        assertThatThrownBy { BenchmarkOptions.parse(listOf("analyze", "results")).argument(1, "output directory") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("analyze needs the output directory")
    }

    @Test
    suspend fun `an unknown command is a usage error`() {
        assertThat(Benchmark.execute(listOf("plot", "crossroads"))).isEqualTo(2)
    }

    @Test
    fun `the leave-one-out levels disable one pass each between all and none`() {
        val levels = OptimizationLevel.leaveOneOut

        assertThat(levels).hasSize(OptimizationPass.entries.size + 2)
        assertThat(levels.first().disabled).isEmpty()
        assertThat(levels.last().disabled).containsExactlyElementsOf(OptimizationPass.entries)
        assertThat(levels.map { it.name }).doesNotHaveDuplicates().contains("without-copy-propagation", "without-assume-false-propagation")
        assertThat(OptimizationLevel.byName("without-dead-code-removal")!!.disabled).containsExactly(OptimizationPass.DeadCodeRemoval)
    }
}
