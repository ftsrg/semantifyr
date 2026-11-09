/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.common.MetricsLine
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.fakePortfolio
import hu.bme.mit.semantifyr.cli.common.jsonLines
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import hu.bme.mit.semantifyr.verifier.VerificationReport
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MetricsCommandTest {

    @Test
    suspend fun `prints the three size levels of a case`() {
        val result = runCli("metrics", smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val lines = result.stdout.lines()
        assertThat(lines[0]).isEqualTo(SMOKE_CASE)
        assertThat(lines[1]).matches("  input      variables \\d+  operations \\d+  expressions \\d+")
        assertThat(lines[2]).matches("  inlined    variables \\d+  operations \\d+  expressions \\d+")
        assertThat(lines[3]).matches("  flattened  variables \\d+  operations \\d+  expressions \\d+")
    }

    @ParameterizedTest
    @ValueSource(strings = ["all", "none"])
    suspend fun `the json sizes are the ones report json records`(optimization: String, @TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)
        val verify = runCli("verify", "--json", "--optimization", optimization, "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))
        val report = Path(jsonLines<VerifyOutcomeLine>(verify.stdout).single().report)
        val compilation = Json.decodeFromString<VerificationReport>(report.readText()).result.metrics.verifier.compilation

        val result = runCli("metrics", "--json", "--optimization", optimization, smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = jsonLines<MetricsLine>(result.stdout).single()
        assertThat(line.case).isEqualTo(SMOKE_CASE)
        assertThat(line.inputSize).isEqualTo(compilation.inputSize)
        assertThat(line.inlinedSize).isEqualTo(compilation.inlinedSize)
        assertThat(line.flattenedSize).isEqualTo(compilation.flattenedSize)
    }

    @Test
    suspend fun `prints one line per selected case`() {
        val result = runCli("metrics", "--json", "--include-tag", "expect-fail", testModels.resolve("semantic/local_vars.oxsts").toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(jsonLines<MetricsLine>(result.stdout)).isNotEmpty()
    }

    @Test
    suspend fun `the json line carries every size field even when it is zero`(@TempDir directory: Path) {
        val model = directory.resolve("stateless.oxsts")
        model.writeText(
            """
            package stateless

            @VerificationCase
            class Stateless {
                prop {
                    return AG true
                }
            }
            """.trimIndent(),
        )

        val result = runCli("metrics", "--json", model.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = result.stdout.trim()
        assertThat(line).contains("\"variables\":0")
        for (level in listOf("inputSize", "inlinedSize", "flattenedSize")) {
            assertThat(line).containsPattern("\"$level\":\\{\"variables\":\\d+,\"operations\":\\d+,\"expressions\":\\d+}")
        }
    }
}
