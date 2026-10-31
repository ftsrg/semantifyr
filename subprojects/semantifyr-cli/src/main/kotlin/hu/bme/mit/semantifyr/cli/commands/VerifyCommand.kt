/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.cli.CommandExit
import hu.bme.mit.semantifyr.cli.ExitCode
import hu.bme.mit.semantifyr.cli.commands.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.qualifiedNameToDirectoryName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

enum class VerifyOutput { Text, Quiet, Json }

class VerifyCommand @Inject constructor(
    semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val injector: Injector,
) : BaseSemantifyrCommand("verify", semantifyrLoader) {
    private val logger by loggerFactory()

    internal var portfolios: List<VerificationPortfolio> = Portfolios.all

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val backendOptions by BackendOptionGroup { portfolios }
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()

    private val output by mutuallyExclusiveOptions(
        option().switch("-q" to VerifyOutput.Quiet, "--quiet" to VerifyOutput.Quiet)
            .help("Print only the verdict of each case, one per line."),
        option().switch("--json" to VerifyOutput.Json)
            .help("Print one JSON object per case, one per line, with the path of its report.json."),
    ).single().default(VerifyOutput.Text)

    override fun help(context: Context): String {
        return "Verify one or more verification cases of an OXSTS model with the chosen backend preset."
    }

    override suspend fun run() {
        val portfolio = backendOptions.portfolio
        val environment = backendOptions.resolved
        logger.info {
            "verify model=$model libraries=$libraries portfolio=${portfolio.id} environment=$environment timeout=${backendOptions.timeout}"
        }

        checkInputs()
        val availability = portfolio.availability(environment)
        if (availability is AvailabilityReport.Unavailable) {
            val hints = availability.hints.joinToString("") { "\n  - $it" }
            throw CommandExit(ExitCode.Errored, "Portfolio '${portfolio.id}' is unavailable: ${availability.reason}$hints")
        }

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)

        val verifier = SemantifyrVerifier
            .builder()
            .injector(injector)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(backendOptions.timeout)
            .artifacts(artifactOptions.resolved)
            .outputDirectory(artifactOptions.resolvedOutputDirectory)
            .optimization(compilationOptions.resolved)
            .build()

        val exitCode = cases.maxOf {
            verifyCase(verifier, it)
        }
        if (exitCode != ExitCode.Success) {
            throw CommandExit(exitCode)
        }
    }

    private suspend fun verifyCase(
        verifier: SemantifyrVerifier,
        case: VerificationCase,
    ): ExitCode {
        if (output == VerifyOutput.Text) {
            echo("Verifying ${case.qualifiedName} ...")
        }
        val result = try {
            verifier.verify(case)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val message = exception.message ?: exception::class.simpleName
            if (output == VerifyOutput.Json) {
                echo(
                    buildJsonObject {
                        put("case", case.qualifiedName)
                        put("exitCode", ExitCode.Compilation.code)
                        put("error", message)
                    },
                )
            }
            throw CommandExit(ExitCode.Compilation, "Compiling ${case.qualifiedName} failed: $message")
        }
        val exitCode = ExitCode.of(result.verdict)
        val report = artifactOptions.resolvedOutputDirectory
            .resolve(qualifiedNameToDirectoryName(case.qualifiedName))
            .resolve("report.json")
            .toAbsolutePath()
            .normalize()
        when (output) {
            VerifyOutput.Text -> {
                val message = result.message?.let { " - $it" } ?: ""
                echo("  ${verdictTag(result.verdict)} (${result.metrics.totalDuration})$message")
                echo("  report: $report")
            }
            VerifyOutput.Quiet -> {
                echo(verdictTag(result.verdict))
            }
            VerifyOutput.Json -> {
                echo(
                    buildJsonObject {
                        put("case", case.qualifiedName)
                        put("exitCode", exitCode.code)
                        put("report", report.toString())
                    },
                )
            }
        }
        return exitCode
    }

    private fun verdictTag(verdict: VerificationVerdict): String {
        return when (verdict) {
            VerificationVerdict.Passed -> "PASSED"
            VerificationVerdict.Failed -> "FAILED"
            VerificationVerdict.Inconclusive -> "INCONCLUSIVE"
            VerificationVerdict.Timeout -> "TIMEOUT"
            VerificationVerdict.Errored -> "ERRORED"
            VerificationVerdict.NotSupported -> "NOT_SUPPORTED"
        }
    }
}
