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
import hu.bme.mit.semantifyr.cli.common.CaseOutcome
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.cli.common.verifyCase
import hu.bme.mit.semantifyr.cli.common.verifyOutputOption
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

class VerifyCommand @Inject constructor(
    private val semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val injector: Injector,
) : BaseSemantifyrCommand("verify", semantifyrLoader) {
    private val logger by loggerFactory()

    internal var portfolios: List<VerificationPortfolio> = Portfolios.all

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val backendOptions by BackendOptionGroup { portfolios }
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()
    private val output by verifyOutputOption()

    override fun help(context: Context): String {
        return "Verify the cases of an OXSTS model."
    }

    override suspend fun run() {
        val portfolio = backendOptions.portfolio
        logger.info {
            "verify model=$model libraries=$libraries portfolio=${portfolio.id} environment=${backendOptions.resolved} timeout=${backendOptions.timeout}"
        }

        checkInputs()
        artifactOptions.checkOutputDirectory()
        backendOptions.checkAvailable()

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)

        val verifier = SemantifyrVerifier
            .builder()
            .injector(injector)
            .portfolio(portfolio)
            .environment(backendOptions.resolved)
            .timeout(backendOptions.timeout)
            .artifacts(artifactOptions.resolved)
            .outputDirectory(artifactOptions.resolvedOutputDirectory)
            .optimization(compilationOptions.resolved)
            .build()

        val witnessPath = System.getProperty("semantifyr.spike.witness")
        if (witnessPath != null) {
            val witnessContext = semantifyrLoader
                .startContext()
                .loadLibraryPaths(libraries)
                .loadModelPaths(listOf(model))
                .loadModel(java.nio.file.Path.of(witnessPath))
                .buildAndResolve()
            val witness = witnessContext.modelResources.last().contents.filterIsInstance<hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts>().single()
            val result = verifier.verify(witness)
            println("spike witness verdict=${result.verdict} message=${result.message}")
            return
        }

        val exitCode = cases.maxOf { case ->
            verifyCase(output, case.qualifiedName) {
                CaseOutcome(verifier.verify(case), caseReportPath(artifactOptions.resolvedOutputDirectory, case))
            }
        }
        if (exitCode != ExitCode.Success) {
            throw CommandExit(exitCode)
        }
    }
}
