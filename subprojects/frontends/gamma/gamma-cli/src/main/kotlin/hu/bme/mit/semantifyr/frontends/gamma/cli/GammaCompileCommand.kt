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
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.io.path.nameWithoutExtension

class GammaCompileCommand(
    private val gammaInjector: Injector,
) : SuspendingCliktCommand("compile") {
    private val logger by loggerFactory()

    override val printHelpOnEmptyArgs = true

    private val model by argument("model path")
        .path()
        .help("A Gamma model file, ending in .gamma or .gcd.")

    private val outputPath by option("-o", "--output")
        .path(canBeDir = false)
        .help("The output file; the model's path with .oxsts in place of its ending by default.")

    private val workspace by option("--workspace")
        .path(mustExist = true, canBeFile = false)
        .help("The directory whose Gamma files, in it and below it, the model's imports resolve in; the model's own directory by default.")

    override fun help(context: Context): String {
        return "Compile a Gamma model to OXSTS."
    }

    override suspend fun run() {
        logger.info {
            "compile model=$model output=$outputPath"
        }

        checkGammaModel(model)
        val target = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.oxsts")
        val gammaModel = readGammaModel(gammaInjector, model, workspace)

        echo("Compiling $model to $target")
        try {
            gammaInjector.getInstance(GammaCompiler::class.java).compile(gammaModel, target)
        } catch (exception: Exception) {
            throw CommandExit(ExitCode.Compilation, exception.message ?: exception::class.simpleName)
        }
    }
}
