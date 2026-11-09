/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.fakePortfolio
import hu.bme.mit.semantifyr.cli.common.jsonLines
import hu.bme.mit.semantifyr.verifier.VerificationReport
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GammaVerifyCommandTest {

    @Test
    suspend fun `every case of the model is verified and the highest exit code wins`(@TempDir artifacts: Path) {
        val verdicts = Array(8) {
            VerificationVerdict.Passed
        }
        verdicts[2] = VerificationVerdict.Failed
        val portfolio = fakePortfolio("fixed", *verdicts)

        val result = runGammaCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), simpleModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(5)
        val lines = jsonLines<VerifyOutcomeLine>(result.stdout)
        assertThat(lines).hasSize(8)
        assertThat(
            lines.map {
                it.case
            },
        ).contains(SIMPLE_CASE).allMatch {
            it.startsWith("Simple.")
        }
        assertThat(
            lines.map {
                it.exitCode
            },
        ).containsOnly(0, 5)
    }

    @Test
    suspend fun `a selected case names its report`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runGammaCli(
            "verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(),
            "--case-name", SIMPLE_CASE,
            simpleModel.toString(),
            portfolios = listOf(portfolio),
        )

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = jsonLines<VerifyOutcomeLine>(result.stdout).single()
        assertThat(line.case).isEqualTo(SIMPLE_CASE)
        val report = Path(line.report)
        assertThat(report).isAbsolute().startsWith(artifacts.toAbsolutePath())
        assertThat(Json.decodeFromString<VerificationReport>(report.readText()).result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `quiet mode prints only the verdict`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Failed)

        val result = runGammaCli("verify", "-q", "--case-name", SIMPLE_CASE, "--portfolio", portfolio.id, "-d", artifacts.toString(), simpleModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(5)
        assertThat(result.stdout).isEqualTo("FAILED\n")
    }

    @Test
    suspend fun `a case name that names no case is an input error`() {
        val result = runGammaCli("verify", "--case-name", "Simple.Absent", simpleModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("Simple.Absent")
    }

    @Test
    suspend fun `an OXSTS model is an input error`(@TempDir directory: Path) {
        val model = directory.resolve("model.oxsts")
        model.writeText("package model\n")

        val result = runGammaCli("verify", model.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains(".gamma")
    }

    @Test
    suspend fun `a model with a syntax error is a compilation error`(@TempDir directory: Path) {
        val model = directory.resolve("Broken.gamma")
        model.writeText("package Broken\n\nstatechart {\n")
        val portfolio = fakePortfolio("fixed")

        val result = runGammaCli("verify", "--portfolio", portfolio.id, model.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(4)
        assertThat(result.stderr).contains("Broken.gamma")
    }

    @Test
    suspend fun `the root command names its launcher`() {
        val result = runGammaCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr-gamma version ")
    }
}
