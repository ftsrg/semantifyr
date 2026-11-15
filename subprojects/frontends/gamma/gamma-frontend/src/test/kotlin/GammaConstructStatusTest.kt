/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
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

@InjectWithGamma
class GammaConstructStatusTest {

    data class ConstructRow(
        val area: String,
        val construct: String,
        val outcome: ConstructOutcome,
        val warning: String,
        val fixture: String,
        val marker: String,
        val detail: String,
    )

    companion object {
        private val gammaRoot: Path = Path("..")
        private val columnNames = listOf(
            "area",
            "construct",
            "outcome",
            "warning",
            "fixture",
            "marker",
            "detail",
        )

        private fun lines(): List<List<String>> {
            val lines = gammaRoot.resolve("models/constructs.tsv").readLines()
            check(lines.first() == columnNames.joinToString("\t")) {
                "constructs.tsv must start with its header"
            }
            return lines.drop(1).filter {
                it.isNotBlank()
            }.map {
                it.split("\t")
            }
        }

        private fun nameOf(columns: List<String>): String {
            return columns.getOrElse(1) {
                columns.first()
            }
        }

        private fun rowOf(columns: List<String>): ConstructRow {
            val construct = nameOf(columns)
            val outcome = ConstructOutcome.entries.firstOrNull {
                it.word == columns.getOrNull(2)
            }
            val problems = mutableListOf<String>()
            if (columns.size != columnNames.size) {
                problems += "${columns.size} columns, where the header has ${columnNames.size}, ${columnNames.joinToString(", ")}"
            }
            if (outcome == null) {
                val words = ConstructOutcome.entries.joinToString(", ") {
                    it.word
                }
                problems += "the outcome '${columns.getOrNull(2)}', where the outcomes are $words"
            }
            check(problems.isEmpty() && outcome != null) {
                "The row '$construct' has ${problems.joinToString(" and ")}"
            }
            return ConstructRow(columns[0], columns[1], outcome, columns[3], columns[4], columns[5], columns[6])
        }

        @JvmStatic
        fun constructs(): List<Arguments> {
            return lines().map {
                Arguments.argumentSet(nameOf(it), it)
            }
        }
    }

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    @Test
    fun `every construct is listed once`() {
        val names = lines().map {
            nameOf(it)
        }
        assertThat(names).doesNotHaveDuplicates()
    }

    @ParameterizedTest
    @MethodSource("constructs")
    fun `construct has the outcome the table claims`(columns: List<String>) {
        val row = rowOf(columns)
        assertThat(row.area).isNotBlank()
        assertThat(row.warning).describedAs("the warning of '${row.construct}'").isIn("yes", "no")
        when (row.outcome) {
            ConstructOutcome.PLANNED, ConstructOutcome.UNSUPPORTED -> {
                assertThat(row.fixture).describedAs("a ${row.outcome.word} construct has no fixture").isEmpty()
                assertThat(row.marker).isEmpty()
                assertThat(row.warning).describedAs("a ${row.outcome.word} construct draws no warning").isEqualTo("no")
            }
            else -> {
                assertFixtureUsesMarker(row)
            }
        }
        if (row.outcome != ConstructOutcome.CONFORMANT) {
            assertThat(row.detail).describedAs("a ${row.outcome.word} construct says why").isNotBlank()
        }
    }

    @ParameterizedTest
    @MethodSource("constructs")
    fun `a construct's fixture draws a warning on its marker exactly where the table says so`(columns: List<String>) {
        val row = rowOf(columns)
        if (!row.fixture.endsWith(".gamma")) {
            return
        }
        val warned = warnedLines(row.fixture).any {
            it.contains(row.marker)
        }
        assertThat(warned)
            .describedAs("the validator warns on '${row.marker}' in ${row.fixture}, where the table's warning of '${row.construct}' is ${row.warning}")
            .isEqualTo(row.warning == "yes")
    }

    private fun warnedLines(fixture: String): List<String> {
        val path = gammaRoot.resolve(fixture)
        val resource = resourceSetProvider.get().getResource(URI.createFileURI(path.absolutePathString()), true)
        val lines = path.readLines()
        return validationTestHelper.validate(resource).filter {
            it.severity == Severity.WARNING
        }.map {
            lines[it.lineNumber - 1]
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
