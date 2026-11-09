/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.common.CaseOutcome
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.checkInputFile
import hu.bme.mit.semantifyr.cli.common.checkInputPath
import hu.bme.mit.semantifyr.cli.common.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.BackendOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.cli.common.verifyCase
import hu.bme.mit.semantifyr.cli.common.verifyOutputOption
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio

class SysMLVerifyCommand @Inject constructor(
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val injector: Injector,
) : SuspendingCliktCommand("verify") {
    private val logger by loggerFactory()

    internal var portfolios: List<VerificationPortfolio> = Portfolios.all

    override val printHelpOnEmptyArgs = true

    private val model by argument("model path")
        .path()
        .help(MODEL_HELP)

    private val libraries by option("-l", "--library")
        .path()
        .multiple()
        .help("OXSTS library file or directory, repeatable.")

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val backendOptions by BackendOptionGroup {
        portfolios
    }
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()
    private val output by verifyOutputOption()

    override fun help(context: Context): String {
        return "Verify the cases of a SysML v2 model."
    }

    override suspend fun run() {
        val portfolio = backendOptions.portfolio
        logger.info {
            "verify model=$model libraries=$libraries portfolio=${portfolio.id} environment=${backendOptions.resolved} timeout=${backendOptions.timeout}"
        }

        checkInputFile("Model", model, SYSML_FILE_SUFFIX)
        for (library in libraries) {
            checkInputPath("Library", library, OxstsLibrary.FILE_NAME_SUFFIX)
        }
        artifactOptions.checkOutputDirectory()
        backendOptions.checkAvailable()

        val (frontend, context) = try {
            val frontend = SysMLv2Frontend.builder()
                .source(model)
                .additionalLibraryPaths(libraries)
                .injector(injector)
                .portfolio(portfolio)
                .environment(backendOptions.resolved)
                .timeout(backendOptions.timeout)
                .artifacts(artifactOptions.resolved)
                .outputDirectory(artifactOptions.resolvedOutputDirectory)
                .optimization(compilationOptions.resolved)
                .build()
            frontend to frontend.modelContext
        } catch (exception: Exception) {
            throw CommandExit(ExitCode.Compilation, exception.message ?: exception::class.simpleName)
        }
        val cases = caseSpecificationOptions.collectVerificationCases(context)

        val exitCode = cases.maxOf { case ->
            verifyCase(output, case.qualifiedName) {
                CaseOutcome(frontend.verify(case), caseReportPath(artifactOptions.resolvedOutputDirectory, case))
            }
        }
        if (exitCode != ExitCode.Success) {
            throw CommandExit(exitCode)
        }
    }
}
