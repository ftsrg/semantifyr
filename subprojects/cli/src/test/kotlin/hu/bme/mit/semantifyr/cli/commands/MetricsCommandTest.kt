/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.fakePortfolio
import hu.bme.mit.semantifyr.cli.jsonLines
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

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
        val report = Path(jsonLines(verify.stdout).single().getValue("report").jsonPrimitive.content)
        val compilation = Json.parseToJsonElement(report.readText()).jsonObject
            .getValue("result").jsonObject
            .getValue("metrics").jsonObject
            .getValue("verifier").jsonObject
            .getValue("compilation").jsonObject

        val result = runCli("metrics", "--json", "--optimization", optimization, smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = jsonLines(result.stdout).single()
        assertThat(line.getValue("case").jsonPrimitive.content).isEqualTo(SMOKE_CASE)
        for (level in listOf("inputSize", "inlinedSize", "flattenedSize")) {
            assertThat(line.getValue(level)).describedAs(level).isEqualTo(compilation.getValue(level))
        }
    }

    @Test
    suspend fun `prints one line per selected case`() {
        val result = runCli("metrics", "--json", "--include-tag", "expect-fail", testModels.resolve("semantic/local_vars.oxsts").toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(jsonLines(result.stdout)).isNotEmpty().allSatisfy {
            assertThat(it.keys).containsExactly("case", "inputSize", "inlinedSize", "flattenedSize")
        }
    }
}
