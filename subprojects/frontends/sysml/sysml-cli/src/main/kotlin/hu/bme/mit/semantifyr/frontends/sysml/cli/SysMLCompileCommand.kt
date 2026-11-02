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
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.checkInputFile
import hu.bme.mit.semantifyr.frontends.sysml.wrapper.SysMLv2Compiler
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.io.path.nameWithoutExtension

class SysMLCompileCommand : SuspendingCliktCommand("compile") {
    private val logger by loggerFactory()

    override val printHelpOnEmptyArgs = true

    private val model by argument("model path")
        .path()
        .help(MODEL_HELP)

    private val outputPath by option("-o", "--output")
        .path(canBeDir = false)
        .help("Where to write the OXSTS model. Defaults to a sibling of the input model with the .oxsts extension.")

    override fun help(context: Context): String {
        return "Translate a SysML v2 model to an OXSTS model over the SysML v2 semantic library."
    }

    override suspend fun run() {
        logger.info { "compile model=$model output=$outputPath" }

        checkInputFile("Model", model, SYSML_FILE_SUFFIX)
        val target = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.oxsts")

        echo("Compiling $model to $target")
        try {
            SysMLv2Compiler().compile(model, target)
        } catch (exception: Exception) {
            throw CommandExit(ExitCode.Compilation, exception.message ?: exception::class.simpleName)
        }
    }
}
