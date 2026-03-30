/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.frontends.gamma.semantics.StandaloneGammaTransformer

class CompileCommand : CliktCommand("compile") {

    val modelPath by argument("-m", "--model-path").file(mustExist = true, canBeFile = true)
    val outputPath by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        val transformer = StandaloneGammaTransformer()
        transformer.transformModel(modelPath, outputPath)
    }

}
