/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.cli.common.fakePortfolio
import hu.bme.mit.semantifyr.cli.common.jsonLines
import hu.bme.mit.semantifyr.frontends.sysml.wrapper.SysMLv2Compiler
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.resource.FileExtensionProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class SysMLVerifyCommandTest {

    private val compiler = mock<SysMLv2Compiler>()

    private val transformed = """
        package semantifyr::sysml::model

        @VerificationCase
        class TrueReachable {
            redefine prop {
                return EF true
            }
        }

        @VerificationCase
        class FalseReachable {
            redefine prop {
                return EF false
            }
        }
    """.trimIndent()

    private suspend fun verifyTransformed(portfolio: VerificationPortfolio, vararg arguments: String): CliktCommandTestResult {
        val command = SysMLVerifyCommand(oxstsInjector.getInstance(VerificationCaseDiscoverer::class.java), oxstsInjector, oxstsInjector.getInstance(FileExtensionProvider::class.java), compiler)
        command.portfolios = listOf(portfolio)
        return command.test(arguments.toList()) {
            command.execute(it)
        }
    }

    @Test
    suspend fun `the verify command transforms the model and reports the verdicts of its cases`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml").createFile()
        val artifacts = directory.resolve("artifacts")
        whenever(compiler.compile(any(), any())).thenAnswer {
            it.getArgument<Path>(1).createParentDirectories().apply {
                writeText(transformed)
            }
        }
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed, VerificationVerdict.Failed)

        val result = verifyTransformed(portfolio, "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), model.toString())

        verify(compiler).compile(eq(model), any())
        assertThat(result.statusCode).describedAs(result.output).isEqualTo(5)
        val lines = jsonLines<VerifyOutcomeLine>(result.stdout)
        assertThat(
            lines.map {
                it.case
            },
        ).containsExactly(
            "semantifyr::sysml::model::TrueReachable",
            "semantifyr::sysml::model::FalseReachable",
        )
        assertThat(Path(lines.first().report))
            .isEqualTo(artifacts.resolve("room.semantifyr.sysml.model.TrueReachable").resolve("report.json").toAbsolutePath().normalize())
            .exists()
    }

    @Test
    suspend fun `a failed transformation is a compilation error of the verify command`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml").createFile()
        whenever(compiler.compile(any(), any())).thenThrow(IllegalStateException("the model does not transform"))
        val portfolio = fakePortfolio("fixed")

        val result = verifyTransformed(portfolio, "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), model.toString())

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(ExitCode.Compilation.code)
        assertThat(result.stderr).contains("the model does not transform")
    }

    @Test
    suspend fun `a model directory is an input error`() {
        val result = runSysMLCli("verify", smokeModel.parent.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains(".sysml")
    }

    @Test
    suspend fun `a missing library path is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("verify", "-l", directory.resolve("absent").toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("Library path")
    }

    @Test
    suspend fun `a library of another kind is an input error naming the extensions of OXSTS`() {
        val result = runSysMLCli("verify", "-l", smokeModel.toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        for (extension in oxstsExtensions) {
            assertThat(result.stderr).contains(".$extension")
        }
    }

    @Test
    suspend fun `the root command names its launcher`() {
        val result = runSysMLCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr-sysml version ")
    }

    @Test
    suspend fun `the launcher can list the portfolios`() {
        val result = runSysMLCli("--help")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).containsPattern("\\n\\s+portfolios\\s+List the portfolios")
    }
}
