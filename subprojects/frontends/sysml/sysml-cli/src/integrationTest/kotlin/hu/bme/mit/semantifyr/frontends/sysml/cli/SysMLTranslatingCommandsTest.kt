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
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SysMLTranslatingCommandsTest {

    private fun copyTutorialModel(directory: Path): Path {
        return tutorialModel.copyTo(directory.resolve("model").createDirectories().resolve("room.sysml"))
    }

    @Test
    suspend fun `the compile command translates a model through the bundled translator`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = copyTutorialModel(directory)

        val result = runSysMLCli("compile", model.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(model.resolveSibling("room.semantifyr").readText()).contains("LampLitReachable")
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
            .isEqualTo(artifacts.resolve("room.semantifyr.sysml.model.LampLitReachable").resolve("report.json").toAbsolutePath().normalize())
            .exists()
    }

    @Test
    suspend fun `a library adds to the bundled SysML library`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = copyTutorialModel(directory)
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runSysMLCli(
            "verify", "-q", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(),
            "-l", semanticModels.resolve("arithmetic.semantifyr").toString(),
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
}
