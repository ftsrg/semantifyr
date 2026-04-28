/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

private fun isOxstsFileOrDirectory(path: Path): Boolean {
    return Files.isDirectory(path) || path.extension == OxstsLibrary.FILE_NAME_SUFFIX
}

abstract class BaseSemantifyrCommand(
    name: String,
    private val semantifyrLoader: SemantifyrLoader,
) : SuspendingCliktCommand(name) {
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
        return semantifyrLoader
            .startContext()
            .loadLibraryPaths(libraries)
            .loadModelPaths(listOf(model))
            .buildAndResolve()
    }
}
