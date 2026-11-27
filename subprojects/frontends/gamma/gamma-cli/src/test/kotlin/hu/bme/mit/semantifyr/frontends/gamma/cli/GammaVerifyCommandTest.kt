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
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed, VerificationVerdict.Failed)

        val result = runGammaCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(5)
        val lines = jsonLines<VerifyOutcomeLine>(result.stdout)
        assertThat(lines).hasSize(2)
        assertThat(
            lines.map {
                it.case
            },
        ).contains(SMOKE_CASE).allMatch {
            it.startsWith("smoke.")
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
            "--case-name", SMOKE_CASE,
            smokeModel.toString(),
            portfolios = listOf(portfolio),
        )

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = jsonLines<VerifyOutcomeLine>(result.stdout).single()
        assertThat(line.case).isEqualTo(SMOKE_CASE)
        val report = Path(line.report)
        assertThat(report).isAbsolute().startsWith(artifacts.toAbsolutePath())
        assertThat(Json.decodeFromString<VerificationReport>(report.readText()).result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `quiet mode prints only the verdict`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Failed)

        val result = runGammaCli("verify", "-q", "--case-name", SMOKE_CASE, "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(5)
        assertThat(result.stdout).isEqualTo("FAILED\n")
    }

    @Test
    suspend fun `a case name that names no case is an input error`() {
        val result = runGammaCli("verify", "--case-name", "smoke.Absent", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("smoke.Absent")
    }

    @Test
    suspend fun `an OXSTS model is an input error`(@TempDir directory: Path) {
        val model = directory.resolve("model.semantifyr")
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
    suspend fun `a workspace resolves imports from another directory`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)
        val workspace = Path("src/test/resources/hu/bme/mit/semantifyr/frontends/gamma/cli/GammaVerifyCommandTest/workspace")
        val model = workspace.resolve("models/Panel.gamma")

        val withoutWorkspace = runGammaCli("verify", "--portfolio", portfolio.id, "-d", artifacts.toString(), model.toString(), portfolios = listOf(portfolio))
        val withWorkspace = runGammaCli(
            "verify", "--portfolio", portfolio.id, "-d", artifacts.toString(),
            "--workspace", workspace.toString(),
            model.toString(),
            portfolios = listOf(portfolio),
        )

        assertThat(withoutWorkspace.statusCode).describedAs(withoutWorkspace.output).isEqualTo(4)
        assertThat(withoutWorkspace.stderr).contains("Toggle")
        assertThat(withWorkspace.statusCode).describedAs(withWorkspace.output).isZero()
    }

    @Test
    suspend fun `the root command names its launcher`() {
        val result = runGammaCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr-gamma version ")
    }

    @Test
    suspend fun `the launcher can list the portfolios`() {
        val result = runGammaCli("--help")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).containsPattern("\\n\\s+portfolios\\s+List the portfolios")
    }
}
