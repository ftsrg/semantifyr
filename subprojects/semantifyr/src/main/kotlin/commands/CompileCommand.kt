/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.ArtifactManager
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.DefaultArtifactManager
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import java.io.File

class CompileCommand : CliktCommand("compile") {

    val logger by loggerFactory()

    val model by argument().file(mustExist = true, canBeFile = true)
    val libraryDirectory by argument().file(mustExist = true, canBeDir = true)
    val targetName by argument()
    val output by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        logger.info("Preparing Xtext Language")

        prepareOxsts()

        logger.info("Reading model $model")

        val reader = OxstsReader(libraryDirectory.path)

        if (model.isFile) {
            reader.readModel(model.path)
        } else {
            reader.readDirectory(model.path)
        }

        logger.info("Compiling target $targetName")

        DefaultArtifactManager.patternPersistor.isEnabled = true
        DefaultArtifactManager.intermediateXstsPersistor.isEnabled = true
        DefaultArtifactManager.instancePersistor.isEnabled = true

        val transformer = XstsTransformer(reader)

        val xsts = transformer.transform(targetName, rewriteChoice = true)
        val xstsString = XstsSerializer.serialize(xsts)

        val outputFile = output ?: File(model.path.replace(".oxsts", ".xsts"))

        logger.info("Producing xsts file to $outputFile")

        logger.info("Artifacts: ${DefaultArtifactManager.baseDirectory.path}")

        outputFile.writeText(xstsString)
    }

}
