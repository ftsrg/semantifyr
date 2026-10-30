/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.examples

import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: CompileGammaExample <sourceFile> <targetFile>")
        exitProcess(2)
    }
    val source = Path.of(args[0])
    val target = Path.of(args[1])
    require(Files.isRegularFile(source)) { "sourceFile must be a regular file: $source" }
    target.parent?.createDirectories()

    val gammaInjector = GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
    val gammaReader = gammaInjector.getInstance(GammaReader::class.java)
    val gammaCompiler = gammaInjector.getInstance(GammaCompiler::class.java)

    println("Compiling $source -> $target")
    val gammaModel = gammaReader.readGammaFile(source.toFile())
    gammaCompiler.compile(gammaModel, target)
}
