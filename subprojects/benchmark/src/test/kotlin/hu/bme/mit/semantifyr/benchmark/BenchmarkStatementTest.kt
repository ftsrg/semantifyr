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

class CellBenchmark : Benchmark<BackendAnswer>() {
    override fun measure(): BenchmarkRun<BackendAnswer> {
        val run = BenchmarkRun<BackendAnswer>()
        repeat(5) {
            val measured = inThisJvm {
                BackendAnswer("Passed", null)
            }
            run.add("crossroads/all", ModelCell("crossroads", emptySet()), measured)
        }
        return run
    }
}

class BenchmarkStatementTest {

    @TempDir
    lateinit var outputDirectory: Path

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
            measureAndWrite(CellBenchmark(), outputDirectory.resolve("absent").resolve("runexec").toString(), outputDirectory)
        }.isInstanceOf(RunexecUnavailableException::class.java).hasMessageContaining("runexec is missing")
        assertThat(outputDirectory.listDirectoryEntries()).isEmpty()
    }
}
