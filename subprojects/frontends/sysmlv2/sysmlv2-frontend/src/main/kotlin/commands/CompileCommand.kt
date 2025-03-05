/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysmlv2.frontend.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.frontends.sysmlv2.frontend.reader.SysMLv2Reader
import hu.bme.mit.semantifyr.frontends.sysmlv2.frontend.reader.prepareSysMLv2
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory

class CompileCommand : CliktCommand("compile") {

    val logger by loggerFactory()

    val modelPath by argument("-m", "--model-path").file(mustExist = true)
    val libraryPath by argument("-m", "--library-path").file(mustExist = true)
    val outputPath by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        logger.info("Preparing SysML v2 Language")
        prepareSysMLv2()

        logger.info("Reading Gamma model")
        val reader = SysMLv2Reader(modelPath, libraryPath)
        val model = reader.readSysMLModel()

//        logger.info("Serializing OXSTS model")
//        val oxstsModel = SysMLv2ToOxstsSerializer.serialize(model)
//        val outputFile = outputPath ?: File("output.oxsts")
//
//        outputFile.writeText(oxstsModel)
    }

}
