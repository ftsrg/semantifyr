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
import hu.bme.mit.semantifyr.cli.common.RootCommand
import hu.bme.mit.semantifyr.cli.common.checkInputFile
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import java.nio.file.Path
import kotlin.io.path.name

private val GAMMA_FILE_SUFFIXES = listOf(
    ".gamma",
    ".gcd",
)

internal fun checkGammaModel(model: Path) {
    val suffix = GAMMA_FILE_SUFFIXES.firstOrNull {
        model.name.endsWith(it)
    } ?: GAMMA_FILE_SUFFIXES.first()
    checkInputFile("Model", model, suffix)
}

class SemantifyrGammaCommand(
    gammaInjector: Injector,
    oxstsInjector: Injector,
) : RootCommand(
    "semantifyr-gamma",
    "Semantifyr for Gamma models.",
    listOf(
        GammaVerifyCommand(gammaInjector, oxstsInjector),
        GammaCompileCommand(gammaInjector),
    ),
)

internal fun readGammaModel(
    gammaInjector: Injector,
    model: Path,
    workspace: Path?,
): GammaModelPackage {
    try {
        return gammaInjector.getInstance(GammaReader::class.java).readGammaFile(model.toFile(), workspace)
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
