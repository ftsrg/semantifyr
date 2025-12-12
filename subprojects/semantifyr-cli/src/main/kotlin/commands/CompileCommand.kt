/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.semantics.transformation.OxstsToXstsTransformer
import hu.bme.mit.semantifyr.semantics.StandaloneSemantifyrModule
import hu.bme.mit.semantifyr.semantics.loading.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory

class CompileCommand : CliktCommand("compile") {

    val logger by loggerFactory()

    val model by argument().file(mustExist = true, canBeFile = true)
    val targetName by argument()
    val library by option("-l", "--library").file(mustExist = true, canBeDir = true, canBeFile = false).multiple()
    val output by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        val loader = StandaloneSemantifyrModule.getInstance<SemantifyrLoader>()

        loader.extraPaths(library.map { it.toPath() })

        val model = loader.loadStandaloneModelContext(model.toPath())

        val transformer = StandaloneSemantifyrModule.getInstance<OxstsToXstsTransformer>()

        transformer.transform(model, "", false)

//        logger.info("Compiling target $targetName")
//
//        val transformer = XstsTransformer(reader)
//
//        val xsts = transformer.transform(targetName, rewriteChoice = true)
//        val xstsString = XstsSerializer.serialize(xsts)
//
//        val outputFile = output ?: File(model.path.replace(".oxsts", ".xsts"))
//
//        logger.info("Producing xsts file to $outputFile")
//
//        outputFile.writeText(xstsString)
    }

}
