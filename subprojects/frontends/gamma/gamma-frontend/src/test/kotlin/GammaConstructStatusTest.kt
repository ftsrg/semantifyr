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

class GammaConstructStatusTest {

    data class ConstructRow(
        val area: String,
        val construct: String,
        val outcome: String,
        val fixture: String,
        val marker: String,
        val detail: String,
    )

    companion object {
        private val gammaRoot: Path = Path("..")
        private val outcomes = setOf("carried", "departure", "planned", "out")

        private fun rows(): List<ConstructRow> {
            val lines = gammaRoot.resolve("models/constructs.tsv").readLines()
            check(lines.first() == "area\tconstruct\toutcome\tfixture\tmarker\tdetail") {
                "constructs.tsv must start with its header"
            }
            return lines.drop(1).filter {
                it.isNotBlank()
            }.map {
                val columns = it.split("\t")
                check(columns.size == 6) {
                    "Expected six tab-separated columns: $it"
                }
                ConstructRow(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5])
            }
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
        assertThat(row.outcome).isIn(outcomes)
        assertThat(row.area).isNotBlank()
        when (row.outcome) {
            "carried", "departure" -> assertFixtureUsesMarker(row)
            else -> {
                assertThat(row.fixture).describedAs("a ${row.outcome} construct has no fixture").isEmpty()
                assertThat(row.marker).isEmpty()
            }
        }
        if (row.outcome != "carried") {
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
