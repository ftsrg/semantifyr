/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import hu.bme.mit.semantifyr.cli.commands.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.verification.AvailabilityReport
import hu.bme.mit.semantifyr.semantics.verification.ExecutionEnvironment
import hu.bme.mit.semantifyr.semantics.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCase
import hu.bme.mit.semantifyr.semantics.verification.VerificationVerdict
import hu.bme.mit.semantifyr.semantics.verification.portfolio.Portfolio
import kotlin.system.exitProcess

class VerifyCommand : BaseSemantifyrCommand("verify") {

    private val logger by loggerFactory()

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup()
    private val backendOptions by BackendOptionGroup()
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()

    override fun help(context: Context): String {
        return "Verify one or more verification cases of an OXSTS model with the chosen backend preset."
    }

    override fun run() {
        val environment = backendOptions.resolved
        val portfolio = backendOptions.resolvePortfolio()
        logger.info { "verify model=$model libraries=$libraries portfolio=${portfolio.id} environment=$environment timeout=${backendOptions.timeout}" }
        ensurePortfolio(portfolio, environment)

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)
        if (cases.isEmpty()) {
            echo("No verification cases match the given filter.", err = true)
            exitProcess(1)
        }

        val artifacts = artifactOptions.resolved

        verifyCases(semantifyrModelContext, portfolio, environment, artifacts, cases)
    }

    private fun ensurePortfolio(portfolio: Portfolio, environment: ExecutionEnvironment) {
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

    private fun verifyCases(
        semantifyrModelContext: SemantifyrModelContext,
        portfolio: Portfolio,
        environment: ExecutionEnvironment,
        artifacts: ArtifactConfig,
        cases: List<VerificationCase>,
    ) {
        SemantifyrVerifier.builder()
            .context(semantifyrModelContext)
            .portfolio(portfolio)
            .environment(environment)
            .timeout(backendOptions.timeout)
            .artifacts(artifacts)
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

    private fun verifyCase(verifier: SemantifyrVerifier, case: VerificationCase): Boolean {
        echo("Verifying ${case.fqn} ...")
        val result = verifier.verifyBlocking(case)
        val resultTag = when (result.verdict) {
            VerificationVerdict.Passed -> "PASSED"
            VerificationVerdict.Failed -> "FAILED"
            VerificationVerdict.Inconclusive -> "INCONCLUSIVE"
            VerificationVerdict.Errored -> "ERRORED"
        }
        val message = result.message?.let { "- $it" } ?: ""
        echo("  $resultTag (${result.metrics.totalDuration}) $message")
        return result.verdict != VerificationVerdict.Passed
    }
}
