/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
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

class BenchmarkStatementTest {

    @TempDir
    lateinit var outputDirectory: Path

    @Test
    fun `the rounds interleave the configurations, each once per round in its stated order`() {
        val scheduled = roundOrder(listOf("b", "a", "c"), 2)

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
    fun `the combinations are every pair of the named values, named after both`() {
        val combined = combinations(
            mapOf(
                "crossroads" to "crossroads",
                "hierarchy" to "hierarchy",
            ),
            mapOf(
                "all" to setOf("copy-propagation"),
                "none" to emptySet(),
            ),
            ::ModelCell,
        )

        assertThat(combined).containsExactly(
            entry("crossroads/all", ModelCell("crossroads", setOf("copy-propagation"))),
            entry("crossroads/none", ModelCell("crossroads", emptySet())),
            entry("hierarchy/all", ModelCell("hierarchy", setOf("copy-propagation"))),
            entry("hierarchy/none", ModelCell("hierarchy", emptySet())),
        )
    }

    @Test
    fun `a missing runexec refuses the run before anything is written`() {
        assertThatThrownBy {
            runInterleavedRounds(
                name = "CellBenchmark",
                cases = mapOf(
                    "crossroads/all" to ModelCell("crossroads", emptySet()),
                ),
                repetitions = 5,
                context = emptyList(),
                configurationSerializer = ModelCell.serializer(),
                resultSerializer = BackendAnswer.serializer(),
                runexecExecutable = outputDirectory.resolve("absent").resolve("runexec").toString(),
                outputDirectory = outputDirectory,
            ) {
                inThisJvm {
                    BackendAnswer("Passed", null)
                }
            }
        }.isInstanceOf(RunexecUnavailableException::class.java).hasMessageContaining("runexec is missing")
        assertThat(outputDirectory.listDirectoryEntries()).isEmpty()
    }
}
