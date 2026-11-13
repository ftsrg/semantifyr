/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import com.github.ajalt.clikt.parsers.CommandLineParser
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.PortfoliosCommand
import hu.bme.mit.semantifyr.cli.common.RootCommand
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import java.nio.file.Path

internal const val GAMMA_FILE_SUFFIX = ".gamma"

class SemantifyrGammaCommand(
    gammaInjector: Injector,
    oxstsInjector: Injector,
) : RootCommand(
    "semantifyr-gamma",
    "Verify Gamma models with Semantifyr, a framework to support the declarative definition of engineering model semantics.",
    listOf(
        GammaVerifyCommand(gammaInjector, oxstsInjector),
        GammaCompileCommand(gammaInjector),
        PortfoliosCommand(),
    ),
)

internal fun readGammaModel(
    gammaInjector: Injector,
    model: Path,
): GammaModelPackage {
    try {
        return gammaInjector.getInstance(GammaReader::class.java).readGammaFile(model.toFile())
    } catch (exception: Exception) {
        throw CommandExit(ExitCode.Compilation, exception.message ?: exception::class.simpleName)
    }
}

suspend fun main(arguments: Array<String>) {
    val gammaInjector = GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
    val oxstsInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    val command = SemantifyrGammaCommand(gammaInjector, oxstsInjector)
    CommandLineParser.main(command) {
        command.execute(arguments.asList())
    }
}
