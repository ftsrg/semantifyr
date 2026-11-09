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
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.common.CaseFailureLine
import hu.bme.mit.semantifyr.cli.common.CasePhase
import hu.bme.mit.semantifyr.cli.common.VerifyOutcomeLine
import hu.bme.mit.semantifyr.cli.common.fakePortfolio
import hu.bme.mit.semantifyr.cli.common.jsonLineFormat
import hu.bme.mit.semantifyr.cli.common.jsonLines
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import hu.bme.mit.semantifyr.cli.writeSameStemModels
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.isWritable
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

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
        val line = jsonLines<VerifyOutcomeLine>(result.stdout).single()
        assertThat(line.case).isEqualTo(SMOKE_CASE)
        assertThat(line.exitCode).isEqualTo(exitCode)
        val report = Path(line.report)
        assertThat(report).isAbsolute().startsWith(artifacts.toAbsolutePath())
        assertThat(Json.decodeFromString<VerificationReport>(report.readText()).result.verdict).isEqualTo(verdict)
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
        assertThat(
            jsonLines<VerifyOutcomeLine>(result.stdout).map {
                it.exitCode
            },
        ).containsExactly(7, 5)
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
        model.writeText(
            """
            package broken

            class {
            """.trimIndent(),
        )

        val result = runCli("verify", "--portfolio", "absent", model.toString(), portfolios = listOf(UnavailablePortfolio))

        assertThat(result.statusCode).isEqualTo(8)
        assertThat(result.stderr).contains("Portfolio 'absent' is unavailable: no engine").contains("install an engine")
    }

    @Test
    suspend fun `a case that fails to compile does not stop the run and exits with the compilation code`(@TempDir directory: Path) {
        val model = directory.resolve("untemporal.oxsts")
        model.writeText(
            """
            package untemporal

            @VerificationCase
            class Untemporal {
                var counter: int := 0

                redefine tran {
                    counter := 1
                }

                prop {
                    return counter == 1
                }
            }

            @VerificationCase
            class Temporal {
                var counter: int := 0

                redefine tran {
                    counter := 1
                }

                prop {
                    return EF counter == 1
                }
            }
            """.trimIndent(),
        )
        val backend = FakeBackend.verdict("counted", VerificationVerdict.Passed)
        val portfolio = SingleBackendPortfolio(backend, FakeConfig("counted"))

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), model.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(4)
        val lines = result.stdout.lines().filter {
            it.isNotBlank()
        }
        assertThat(lines).hasSize(2)
        val failing = jsonLineFormat.decodeFromString<CaseFailureLine>(lines[0])
        val passing = jsonLineFormat.decodeFromString<VerifyOutcomeLine>(lines[1])
        assertThat(failing.case).isEqualTo("untemporal::Untemporal")
        assertThat(failing.exitCode).isEqualTo(4)
        assertThat(failing.phase).isEqualTo(CasePhase.Compilation)
        assertThat(failing.error).contains("temporal operator")
        assertThat(passing.case).isEqualTo("untemporal::Temporal")
        assertThat(passing.exitCode).isZero()
        assertThat(Path(passing.report)).exists()
        assertThat(result.stderr).contains("Compiling untemporal::Untemporal failed").contains("temporal operator")
        assertThat(backend.invocations.get()).isEqualTo(1)
    }

    @Test
    suspend fun `a directory with a model that fails to compile verifies the other models`(@TempDir directory: Path) {
        val models = directory.resolve("models").createDirectory()
        models.resolve("untemporal.oxsts").writeText(
            """
            package untemporal

            @VerificationCase
            class Untemporal {
                var counter: int := 0

                redefine tran {
                    counter := 1
                }

                prop {
                    return counter == 1
                }
            }
            """.trimIndent(),
        )
        smokeModel.copyTo(models.resolve("smoke.oxsts"))
        val backend = FakeBackend.verdict("counted", VerificationVerdict.Passed)
        val portfolio = SingleBackendPortfolio(backend, FakeConfig("counted"))

        val result = runCli("verify", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), models.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(4)
        val lines = result.stdout.lines()
        assertThat(lines).containsSequence("Verifying untemporal::Untemporal ...", "  COMPILATION_ERROR")
        val smokeLine = lines.indexOf("Verifying $SMOKE_CASE ...")
        assertThat(smokeLine).isNotNegative()
        assertThat(lines[smokeLine + 1]).startsWith("  PASSED (")
        assertThat(result.stderr).contains("Compiling untemporal::Untemporal failed")
        assertThat(backend.invocations.get()).isEqualTo(1)
    }

    @Test
    suspend fun `a SysML model is an input error`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml")
        model.writeText("part def Room;\n")

        val result = runCli("verify", model.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("room.sysml").contains(".oxsts")
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

    @Test
    suspend fun `the printed time includes the compilation`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runCli("verify", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val lines = result.stdout.lines()
        val metrics = Json.decodeFromString<VerificationReport>(Path(lines[2].removePrefix("  report: ")).readText()).result.metrics
        assertThat(metrics.verifier.compilationDuration).isGreaterThan(Duration.ZERO)
        assertThat(lines[1]).isEqualTo("  PASSED (${metrics.verifier.compilationDuration + metrics.totalDuration})")
    }

    @Test
    suspend fun `the json line keeps the compilation and verification durations apart`(@TempDir artifacts: Path) {
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed)

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val line = jsonLines<VerifyOutcomeLine>(result.stdout).single()
        val metrics = Json.decodeFromString<VerificationReport>(Path(line.report).readText()).result.metrics
        assertThat(line.compilationDuration).isEqualTo(metrics.verifier.compilationDuration)
        assertThat(line.verificationDuration).isEqualTo(metrics.totalDuration)
    }

    @Test
    suspend fun `cases of models sharing a file stem get their own reports`(@TempDir directory: Path) {
        val models = writeSameStemModels(directory)
        val portfolio = fakePortfolio("fixed", VerificationVerdict.Passed, VerificationVerdict.Passed)

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", directory.resolve("artifacts").toString(), models.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val reports = jsonLines<VerifyOutcomeLine>(result.stdout).map {
            Path(it.report)
        }
        assertThat(reports).hasSize(2).doesNotHaveDuplicates()
        for (report in reports) {
            assertThat(report).exists()
        }
    }

    @Test
    suspend fun `a witness that fails to back-annotate exits with the back-annotation code`(@TempDir artifacts: Path) {
        val portfolio = brokenWitnessPortfolio()

        val result = runCli("verify", "--json", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(9)
        val line = jsonLines<CaseFailureLine>(result.stdout).single()
        assertThat(line.case).isEqualTo(SMOKE_CASE)
        assertThat(line.exitCode).isEqualTo(9)
        assertThat(line.phase).isEqualTo(CasePhase.BackAnnotation)
        assertThat(line.error).contains("broken witness")
        assertThat(result.stderr).contains("Back-annotating the witness of $SMOKE_CASE failed").doesNotContain("Compiling")
    }

    @Test
    suspend fun `a witness that fails to back-annotate is no compilation error in text mode`(@TempDir artifacts: Path) {
        val portfolio = brokenWitnessPortfolio()

        val result = runCli("verify", "--portfolio", portfolio.id, "-d", artifacts.toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).isEqualTo(9)
        assertThat(result.stdout.lines()).containsSequence("Verifying $SMOKE_CASE ...", "  BACK_ANNOTATION_ERROR")
    }

    @Test
    suspend fun `an artifact directory that cannot be written exits with the output code before verifying`(@TempDir directory: Path) {
        val readOnly = directory.resolve("read-only").createDirectory()
        assumeTrue(readOnly.toFile().setWritable(false) && !readOnly.isWritable(), "the file system cannot revoke write access")
        val backend = FakeBackend.verdict("counted", VerificationVerdict.Passed)
        val portfolio = SingleBackendPortfolio(backend, FakeConfig("counted"))

        val result = runCli("verify", "--portfolio", portfolio.id, "-d", readOnly.resolve("artifacts").toString(), smokeModel.toString(), portfolios = listOf(portfolio))

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(10)
        assertThat(result.stdout).isEmpty()
        assertThat(result.stderr).contains("Artifact directory").contains("cannot be written")
        assertThat(backend.invocations.get()).isZero()
    }

    private fun brokenWitnessPortfolio(): SingleBackendPortfolio<FakeConfig> {
        val witness = mock<InlinedWitness> {
            on {
                initializedState
            } doThrow IllegalStateException("broken witness")
        }
        val backend = FakeBackend("broken") {
            BackendVerificationResult(verdict = VerificationVerdict.Failed, metadata = fakeMetadata("broken"), inlinedWitness = witness)
        }
        return SingleBackendPortfolio(backend, FakeConfig("broken"))
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
