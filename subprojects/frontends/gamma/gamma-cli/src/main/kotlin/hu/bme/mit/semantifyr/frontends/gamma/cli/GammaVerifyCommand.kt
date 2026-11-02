/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.common.CaseOutcome
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.checkInputFile
import hu.bme.mit.semantifyr.cli.common.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.common.verifyCase
import hu.bme.mit.semantifyr.cli.common.verifyOutputOption
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

class GammaVerifyCommand(
    private val gammaInjector: Injector,
    private val oxstsInjector: Injector,
) : SuspendingCliktCommand("verify") {
    private val logger by loggerFactory()

    internal var portfolios: List<VerificationPortfolio> = Portfolios.all

    override val printHelpOnEmptyArgs = true

    private val model by argument("model path")
        .path()
        .help("Gamma model file (must end in .gamma).")

    private val caseNames by option("--case-name")
        .multiple()
        .help("The qualified name of a verification case to verify, such as Simple.WorkerStatechartIdleReachable. Repeat the flag to add multiple. Default: every case of the model.")

    private val backendOptions by BackendOptionGroup { portfolios }
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()
    private val output by verifyOutputOption()

    override fun help(context: Context): String {
        return "Compile a Gamma model to OXSTS, verify one or more of its verification cases with the chosen backend preset, and write the Gamma trace of each witness as trace.gamma next to its report.json."
    }

    override suspend fun run() {
        val portfolio = backendOptions.portfolio
        logger.info {
            "verify model=$model portfolio=${portfolio.id} environment=${backendOptions.resolved} timeout=${backendOptions.timeout}"
        }

        checkInputFile("Model", model, GAMMA_FILE_SUFFIX)
        backendOptions.checkAvailable()

        val gammaModel = readGammaModel(gammaInjector, model)
        val cases = selectCases(gammaInjector.getInstance(GammaVerificationCaseDiscoverer::class.java).discover(gammaModel))

        val frontend = GammaFrontend.builder()
            .gammaInjector(gammaInjector)
            .oxstsInjector(oxstsInjector)
            .portfolio(portfolio)
            .environment(backendOptions.resolved)
            .timeout(backendOptions.timeout)
            .artifacts(artifactOptions.resolved)
            .outputDirectory(artifactOptions.resolvedOutputDirectory)
            .optimization(compilationOptions.resolved)
            .build()

        val exitCode = cases.maxOf { case ->
            verifyCase(output, case.qualifiedName) {
                val result = frontend.verify(case)
                CaseOutcome(result.verification, result.reportPath)
            }
        }
        if (exitCode != ExitCode.Success) {
            throw CommandExit(exitCode)
        }
    }

    private fun selectCases(cases: List<GammaVerificationCase>): List<GammaVerificationCase> {
        if (caseNames.isNotEmpty()) {
            return caseNames.map { name ->
                cases.firstOrNull { it.qualifiedName == name }
                    ?: throw CommandExit(ExitCode.Input, "No verification case is named '$name'.")
            }
        }
        if (cases.isEmpty()) {
            throw CommandExit(ExitCode.Input, "No verification case matches the selection.")
        }
        return cases
    }
}
