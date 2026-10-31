/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.fakePortfolio
import hu.bme.mit.semantifyr.cli.jsonLines
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class VerifyCommandTest {

    @ParameterizedTest
    @CsvSource(
        "Passed, 0",
        "Failed, 5",
        "Inconclusive, 6",
        "NotSupported, 6",
        "Timeout, 7",
        "Errored, 8",
    )
    suspend fun `the exit code and the json line follow the verdict`(
        verdict: VerificationVerdict,
        exitCode: Int,
        @TempDir artifacts: Path,
    ) {
        val portfolio = fakePortfolio("fixed", verdict)

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(exitCode)
        val line = jsonLines(result.stdout).single()
        assertThat(line.getValue("case").jsonPrimitive.content).isEqualTo(SMOKE_CASE)
        assertThat(line.getValue("exitCode").jsonPrimitive.int).isEqualTo(exitCode)
        val report = Path(line.getValue("report").jsonPrimitive.content)
        assertThat(report).isAbsolute().startsWith(artifacts.toAbsolutePath())
        val reportResult = Json.parseToJsonElement(report.readText()).jsonObject.getValue("result").jsonObject
        assertThat(reportResult.getValue("verdict").jsonPrimitive.content).isEqualTo(verdict.name)
    }

    @Test
    suspend fun `quiet mode prints only the verdict`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Failed)

        val result = runCli("verify", "-q", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(5)
        assertThat(result.stdout).isEqualTo("FAILED\n")
    }

    @Test
    suspend fun `text mode names the case, the verdict and the report`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runCli("verify", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isZero()
        val lines = result.stdout.lines()
        assertThat(lines[0]).isEqualTo("Verifying $SMOKE_CASE ...")
        assertThat(lines[1]).startsWith("  PASSED (")
        assertThat(lines[2]).startsWith("  report: ").endsWith("report.json")
        assertThat(Path(lines[2].removePrefix("  report: "))).exists()
    }

    @Test
    suspend fun `every case runs and the highest exit code wins`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("sequence", VerificationVerdict.Timeout, VerificationVerdict.Failed)
        val model = testModels.resolve("semantic/arithmetic.oxsts")

        val result = runCli(
            "verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(),
            "--case-name", "semantifyr::verification::lang::arithmetic::AddReach",
            "--case-name", "semantifyr::verification::lang::arithmetic::AddBound",
            model.toString(),
            portfolios = listOf(portfolio),
        )

        assertThat(result.statusCode).isEqualTo(7)
        assertThat(jsonLines(result.stdout).map { it.getValue("exitCode").jsonPrimitive.int }).containsExactly(7, 5)
    }

    @Test
    suspend fun `the verifier timeout exits with the timeout code`(@TempDir artifacts: Path) {
        val portfolio = SingleBackendPortfolio(FakeBackend.delayed("slow", 60_000, VerificationVerdict.Passed), FakeConfig("slow"))

        val result = runCli("verify", "-q", "-t", "1", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(7)
        assertThat(result.stdout).isEqualTo("TIMEOUT\n")
    }

    @Test
    suspend fun `an unavailable portfolio exits with the error code before loading the model`(@TempDir directory: Path) {
        val model = directory.resolve("broken.oxsts")
        model.writeText("package broken\n\nclass {\n")

        val result = runCli("verify", "--portfolio", "absent", model.toString(), portfolios = listOf(UnavailablePortfolio))

        assertThat(result.statusCode).isEqualTo(8)
        assertThat(result.stderr).contains("Portfolio 'absent' is unavailable: no engine").contains("install an engine")
    }

    @Test
    suspend fun `a case that fails to compile stops the run with the compilation code`(@TempDir directory: Path) {
        val model = directory.resolve("untemporal.oxsts")
        model.writeText(
            """
            package untemporal

            @VerificationCase
            class Untemporal {
                var x: int := 0

                redefine tran {
                    x := 1
                }

                prop {
                    return x == 1
                }
            }

            @VerificationCase
            class Temporal {
                var x: int := 0

                redefine tran {
                    x := 1
                }

                prop {
                    return EF x == 1
                }
            }
            """.trimIndent(),
        )
        val backend = FakeBackend.verdict("counted", VerificationVerdict.Passed)
        val portfolio = SingleBackendPortfolio(backend, FakeConfig("counted"))

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), model.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(4)
        val line = jsonLines(result.stdout).single()
        assertThat(line.getValue("case").jsonPrimitive.content).isEqualTo("untemporal::Untemporal")
        assertThat(line.getValue("exitCode").jsonPrimitive.int).isEqualTo(4)
        assertThat(line.getValue("error").jsonPrimitive.content).contains("temporal operator")
        assertThat(result.stderr).contains("untemporal::Untemporal")
        assertThat(backend.invocations.get()).isZero()
    }

    @Test
    suspend fun `an unknown portfolio is a usage error`() {
        val result = runCli("verify", "--portfolio", "no-such-portfolio", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("unknown portfolio 'no-such-portfolio'")
    }

    @Test
    suspend fun `a timeout below one second is a usage error`() {
        val result = runCli("verify", "-t", "0", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("minimum valid value of 1")
    }

    @Test
    suspend fun `quiet and json together are a usage error`() {
        val result = runCli("verify", "--quiet", "--json", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
    }

    @Test
    suspend fun `an artifact directory that is a file is a usage error`(@TempDir directory: Path) {
        val file = directory.resolve("artifacts").createFile()

        val result = runCli("verify", "-d", file.toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("is a file")
    }

    @Test
    suspend fun `a missing model is an input error before the portfolio is probed`(@TempDir directory: Path) {
        val result = runCli("verify", "--portfolio", "absent", directory.resolve("absent.oxsts").toString(), portfolios = listOf(UnavailablePortfolio))

        assertThat(result.statusCode).isEqualTo(3)
    }

    private object UnavailablePortfolio : VerificationPortfolio() {
        override val id = "absent"
        override val displayName = id
        override val description = "test-only"
        override val executorKeys = emptySet<ExecutorKey<*>>()
        override val legs = emptyList<PortfolioLeg>()

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Unavailable("no engine", listOf("install an engine"))
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): BackendVerificationResult {
            error("UnavailablePortfolio.verify was called")
        }
    }
}
