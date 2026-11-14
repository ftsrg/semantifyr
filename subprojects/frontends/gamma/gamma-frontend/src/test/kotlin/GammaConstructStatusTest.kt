/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText

enum class ConstructOutcome(val word: String) {
    CONFORMANT("conformant"),
    ABSTRACTED("abstracted"),
    DEVIATING("deviating"),
    EXTENSION("extension"),
    PLANNED("planned"),
    UNSUPPORTED("unsupported"),
}

class GammaConstructStatusTest {

    data class ConstructRow(
        val area: String,
        val construct: String,
        val outcome: String,
        val warning: String,
        val fixture: String,
        val marker: String,
        val detail: String,
    )

    companion object {
        private val gammaRoot: Path = Path("..")

        private fun rows(): List<ConstructRow> {
            val lines = gammaRoot.resolve("models/constructs.tsv").readLines()
            check(lines.first() == "area\tconstruct\toutcome\twarning\tfixture\tmarker\tdetail") {
                "constructs.tsv must start with its header"
            }
            return lines.drop(1).filter {
                it.isNotBlank()
            }.map {
                val columns = it.split("\t")
                check(columns.size == 7) {
                    "Expected seven tab-separated columns: $it"
                }
                ConstructRow(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6])
            }
        }

        private fun outcomeOf(row: ConstructRow): ConstructOutcome {
            val outcome = ConstructOutcome.entries.firstOrNull {
                it.word == row.outcome
            }
            checkNotNull(outcome) {
                val words = ConstructOutcome.entries.joinToString(", ") {
                    it.word
                }
                "The row '${row.construct}' has the outcome '${row.outcome}', where the outcomes are $words"
            }
            return outcome
        }

        @JvmStatic
        fun constructs(): List<Arguments> {
            return rows().map {
                Arguments.argumentSet(it.construct, it)
            }
        }
    }

    @Test
    fun `every construct is listed once`() {
        val names = rows().map {
            it.construct
        }
        assertThat(names).doesNotHaveDuplicates()
    }

    @ParameterizedTest
    @MethodSource("constructs")
    fun `construct has the outcome the table claims`(row: ConstructRow) {
        val outcome = outcomeOf(row)
        assertThat(row.area).isNotBlank()
        assertThat(row.warning).describedAs("the warning of '${row.construct}'").isIn("yes", "no")
        when (outcome) {
            ConstructOutcome.PLANNED, ConstructOutcome.UNSUPPORTED -> {
                assertThat(row.fixture).describedAs("a ${row.outcome} construct has no fixture").isEmpty()
                assertThat(row.marker).isEmpty()
                assertThat(row.warning).describedAs("a ${row.outcome} construct draws no warning").isEqualTo("no")
            }
            else -> {
                assertFixtureUsesMarker(row)
            }
        }
        if (outcome != ConstructOutcome.CONFORMANT) {
            assertThat(row.detail).describedAs("a ${row.outcome} construct says why").isNotBlank()
        }
    }

    private fun assertFixtureUsesMarker(row: ConstructRow) {
        val fixture = gammaRoot.resolve(row.fixture)
        assertThat(fixture).describedAs("fixture ${row.fixture}").exists()
        assertThat(fixture.readText())
            .describedAs("fixture ${row.fixture} should use '${row.marker}'")
            .contains(row.marker)
        assertThat(row.marker).isNotBlank()
    }
}
