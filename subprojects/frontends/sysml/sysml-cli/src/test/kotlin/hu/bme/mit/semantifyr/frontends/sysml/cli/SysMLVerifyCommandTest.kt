/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.fakePortfolio
import hu.bme.mit.semantifyr.cli.common.jsonLines
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SysMLVerifyCommandTest {

    private fun copyTutorialModel(directory: Path): Path {
        return tutorialModel.copyTo(directory.resolve("model").createDirectories().resolve("room.sysml"))
    }

    @Test
    suspend fun `a SysML model is translated and its cases verified`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = copyTutorialModel(directory)
        val artifacts = directory.resolve("artifacts")
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed, VerificationVerdict.Failed, VerificationVerdict.Failed)

        val result = runSysMLCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), model.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(5)
        val lines = jsonLines<VerifyOutcomeLine>(result.stdout)
        assertThat(
            lines.map {
                it.case
            },
        ).containsExactly(
            "semantifyr::sysml::model::LampLitReachable",
            "semantifyr::sysml::model::LampNeverLitViolated",
            "semantifyr::sysml::model::LampLitOnlyWhileSwitchOnViolated",
        )
        assertThat(Path(lines.first().report))
            .isEqualTo(artifacts.resolve("room.LampLitReachable").resolve("report.json").toAbsolutePath().normalize())
            .exists()
    }

    @Test
    suspend fun `a library adds to the bundled SysML library`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = copyTutorialModel(directory)
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runSysMLCli(
            "verify", "-q", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(),
            "-l", semanticModels.resolve("arithmetic.oxsts").toString(),
            "--case-name", "semantifyr::sysml::model::LampLitReachable",
            model.toString(),
            portfolios = listOf(portfolio),
        )

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).isEqualTo("PASSED\n")
    }

    @Test
    suspend fun `a SysML model that does not translate is a compilation error`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = directory.resolve("model").createDirectories().resolve("broken.sysml")
        model.writeText("part def Broken {\n")
        val portfolio = fakePortfolio("fixed")

        val result = runSysMLCli("verify", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), model.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(4)
    }

    @Test
    suspend fun `an OXSTS model is an input error`() {
        val result = runSysMLCli("verify", semanticModels.resolve("arithmetic.oxsts").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("arithmetic.oxsts").contains(".sysml")
    }

    @Test
    suspend fun `a model directory is an input error`() {
        val result = runSysMLCli("verify", tutorialModel.parent.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains(".sysml")
    }

    @Test
    suspend fun `a missing library path is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("verify", "-l", directory.resolve("absent").toString(), tutorialModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("Library path")
    }

    @Test
    suspend fun `the root command names its launcher`() {
        val result = runSysMLCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr-sysml version ")
    }
}
