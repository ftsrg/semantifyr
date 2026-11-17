/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

@Serializable
data class ModelCell(
    val model: String,
    val passes: Set<String>,
)

@Serializable
data class BackendAnswer(
    val verdict: String,
    val message: String?,
)

abstract class ModuleBenchmark<C> : ProcessBenchmark<C, BackendAnswer>()

abstract class ModuleBenchmarkOfCells : ModuleBenchmark<ModelCell>()

class CellBenchmark : ModuleBenchmarkOfCells() {
    override val configurations = mapOf(
        "crossroads/all" to ModelCell("crossroads", setOf("copy-propagation")),
    )

    override fun repetition(processes: MeasuredProcesses): BackendAnswer {
        return BackendAnswer("Passed", null)
    }
}

class BenchmarkStatementTest {

    @TempDir
    lateinit var outputDirectory: Path

    @Test
    fun `the serializers are found through bases that bind a type argument`() {
        val benchmark = CellBenchmark()

        assertThat(benchmark.configurationSerializer.descriptor).isEqualTo(ModelCell.serializer().descriptor)
        assertThat(benchmark.resultSerializer.descriptor).isEqualTo(BackendAnswer.serializer().descriptor)
    }

    @Test
    fun `the rounds interleave the configurations, each once per round in its stated order`() {
        val scheduled = interleavedRounds(listOf("b", "a", "c"), 2)

        assertThat(scheduled).containsExactly(
            ScheduledRepetition(0, "b"),
            ScheduledRepetition(0, "a"),
            ScheduledRepetition(0, "c"),
            ScheduledRepetition(1, "b"),
            ScheduledRepetition(1, "a"),
            ScheduledRepetition(1, "c"),
        )
    }

    @Test
    fun `a missing runexec refuses the run before anything is written`() {
        assertThatThrownBy {
            runBenchmark(CellBenchmark(), outputDirectory.resolve("absent").resolve("runexec").toString(), outputDirectory)
        }.isInstanceOf(RunexecUnavailableException::class.java).hasMessageContaining("runexec is not available")
        assertThat(outputDirectory.listDirectoryEntries()).isEmpty()
    }
}
