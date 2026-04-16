/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

private const val OXSTS_EXTENSION = "oxsts"

private fun isOxstsFileOrDirectory(path: Path): Boolean {
    return Files.isDirectory(path) || path.extension == OXSTS_EXTENSION
}

private val semantifyrLoader by lazy {
    StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrLoader>()
}

abstract class BaseSemantifyrCommand(name: String) : CliktCommand(name) {

    override val printHelpOnEmptyArgs = true

    val model by argument("model path")
        .path(mustExist = true, canBeFile = true, canBeDir = true)
        .help("OXSTS model file (must end in .oxsts) or directory (walked recursively for .oxsts files).")
        .check("model path must be an .oxsts file or a directory") { path -> isOxstsFileOrDirectory(path) }

    val libraries by option("-l", "--library")
        .path(mustExist = true, canBeFile = true, canBeDir = true)
        .multiple()
        .help("Library file (must end in .oxsts) or directory (walked recursively). Repeat the flag to add multiple.")
        .check("library path must be an .oxsts file or a directory") {
            it.all { isOxstsFileOrDirectory(it) }
        }

    init {
        versionOption(javaClass.`package`.implementationVersion ?: "unknown")
    }

    fun readModelContext(): SemantifyrModelContext {
        return semantifyrLoader.startContext()
            .loadLibraryPaths(libraries)
            .loadModelPaths(listOf(model))
            .buildAndResolve()
    }

}
