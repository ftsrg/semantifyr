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
        .help("Gamma model file.")

    private val outputPath by option("-o", "--output")
        .path(canBeDir = false)
        .help("Output file.")

    private val workspace by option("--workspace")
        .path(mustExist = true, canBeFile = false)
        .help("Directory whose Gamma files, recursively, imports resolve in.")

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
