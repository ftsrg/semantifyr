/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.commands.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import kotlin.system.exitProcess

class VerifyCommand @Inject constructor(
    semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val injector: Injector,
) : BaseSemantifyrCommand("verify", semantifyrLoader) {
    private val logger by loggerFactory()

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val backendOptions by BackendOptionGroup()
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()

    override fun help(context: Context): String {
        return "Verify one or more verification cases of an OXSTS model with the chosen backend preset."
    }

    override suspend fun run() {
        val environment = backendOptions.resolved
        val portfolio = backendOptions.resolvePortfolio()
        logger.info {
            "verify model=$model libraries=$libraries portfolio=${portfolio.id} environment=$environment timeout=${backendOptions.timeout}"
        }
        ensurePortfolio(portfolio, environment)

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)
        if (cases.isEmpty()) {
            echo("No verification cases match the given filter.", err = true)
            exitProcess(1)
        }

        val artifacts = artifactOptions.resolved
        val outputDirectory = artifactOptions.resolvedOutputDirectory

        verifyCases(semantifyrModelContext, portfolio, environment, artifacts, outputDirectory, cases)
    }

    private fun ensurePortfolio(
        portfolio: VerificationPortfolio,
        environment: ExecutionEnvironment,
    ) {
        val availability = portfolio.availability(environment)
        if (availability !is AvailabilityReport.Unavailable) return

        logger.warn { "Portfolio '${portfolio.id}' unavailable: ${availability.reason}" }
        echo("Portfolio '${portfolio.id}' is unavailable: ${availability.reason}", err = true)
        if (availability.hints.isNotEmpty()) {
            echo("Hints:", err = true)
            availability.hints.forEach {
                echo("  - $it", err = true)
            }
        }
        exitProcess(2)
    }

    private suspend fun verifyCases(
        semantifyrModelContext: SemantifyrModelContext,
        portfolio: VerificationPortfolio,
        environment: ExecutionEnvironment,
        artifacts: ArtifactConfig,
        outputDirectory: java.nio.file.Path,
        cases: List<VerificationCase>,
    ) {
        SemantifyrVerifier
            .builder()
            .injector(injector)
            .context(semantifyrModelContext)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(backendOptions.timeout)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .optimization(compilationOptions.resolved)
            .build()
            .use { verifier ->
                var anyNonPass = false
                for (case in cases) {
                    anyNonPass = verifyCase(verifier, case) || anyNonPass
                }
                if (anyNonPass) {
                    exitProcess(1)
                }
            }
    }

    private suspend fun verifyCase(
        verifier: SemantifyrVerifier,
        case: VerificationCase,
    ): Boolean {
        echo("Verifying ${case.qualifiedName} ...")
        val result = verifier.verify(case)
        val resultTag = when (result.verdict) {
            VerificationVerdict.Passed -> "PASSED"
            VerificationVerdict.Failed -> "FAILED"
            VerificationVerdict.Inconclusive -> "INCONCLUSIVE"
            VerificationVerdict.Errored -> "ERRORED"
            VerificationVerdict.NotSupported -> "NOT_SUPPORTED"
        }
        val message = result.message?.let { "- $it" } ?: ""
        echo("  $resultTag (${result.metrics.totalDuration}) $message")
        return result.verdict != VerificationVerdict.Passed
    }
}
