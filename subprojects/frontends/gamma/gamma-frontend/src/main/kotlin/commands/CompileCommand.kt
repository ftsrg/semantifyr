/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.frontend.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.prepareGamma
import hu.bme.mit.semantifyr.frontends.gamma.frontend.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import java.io.File

class CompileCommand : CliktCommand("compile") {

    val logger by loggerFactory()

    val modelPath by argument("-m", "--model-path").file(mustExist = true, canBeFile = true)
    val outputPath by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        logger.info("Preparing Gamma Language")
        prepareGamma()

        logger.info("Reading Gamma model")
        val reader = GammaReader()
        val model = reader.readGammaFile(modelPath)

        logger.info("Serializing OXSTS model")
        val oxstsModel = GammaToOxstsSerializer.serialize(model)
        val outputFile = outputPath ?: File(modelPath.absolutePath.replace(".gamma", ".oxsts"))

        outputFile.writeText(oxstsModel)
    }

}
