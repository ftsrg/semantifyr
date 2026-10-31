/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import hu.bme.mit.semantifyr.cli.CommandExit
import hu.bme.mit.semantifyr.cli.ExitCode
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import hu.bme.mit.semantifyr.verifier.VerificationCase
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

abstract class BaseSemantifyrCommand(
    name: String,
    private val semantifyrLoader: SemantifyrLoader,
) : SuspendingCliktCommand(name) {
    override val printHelpOnEmptyArgs = true

    val model by argument("model path")
        .path()
        .help("OXSTS model file (must end in .oxsts) or directory (walked recursively for .oxsts files).")

    val libraries by option("-l", "--library")
        .path()
        .multiple()
        .help("Library file (must end in .oxsts) or directory (walked recursively). Repeat the flag to add multiple.")

    fun checkInputs() {
        checkInput("Model", model)
        for (library in libraries) {
            checkInput("Library", library)
        }
    }

    fun readModelContext(): SemantifyrModelContext {
        try {
            return semantifyrLoader
                .startContext()
                .loadLibraryPaths(libraries)
                .loadModelPaths(listOf(model))
                .buildAndResolve()
        } catch (exception: Exception) {
            throw CommandExit(ExitCode.Compilation, exception.message ?: exception::class.simpleName)
        }
    }

    fun compileCase(
        compiler: SemantifyrCompiler,
        case: VerificationCase,
        outputDirectory: Path,
    ): FlattenedCompilationContext {
        try {
            return compiler.compile(case.classDeclaration, outputDirectory)
        } catch (exception: Exception) {
            throw CommandExit(ExitCode.Compilation, "Compiling ${case.qualifiedName} failed: ${exception.message ?: exception::class.simpleName}")
        }
    }

    private fun checkInput(kind: String, path: Path) {
        if (!path.exists()) {
            throw CommandExit(ExitCode.Input, "$kind path '$path' does not exist.")
        }
        if (!path.isReadable()) {
            throw CommandExit(ExitCode.Input, "$kind path '$path' is not readable.")
        }
        if (!path.isDirectory()) {
            if (!path.name.endsWith(OxstsLibrary.FILE_NAME_SUFFIX)) {
                throw CommandExit(ExitCode.Input, "$kind path '$path' is neither an ${OxstsLibrary.FILE_NAME_SUFFIX} file nor a directory.")
            }
            return
        }
        val files = try {
            path.walk(PathWalkOption.FOLLOW_LINKS).filter {
                it.isRegularFile() && it.name.endsWith(OxstsLibrary.FILE_NAME_SUFFIX)
            }.toList()
        } catch (exception: IOException) {
            throw CommandExit(ExitCode.Input, "$kind directory '$path' cannot be read: ${exception.message}")
        }
        if (files.isEmpty()) {
            throw CommandExit(ExitCode.Input, "$kind directory '$path' contains no ${OxstsLibrary.FILE_NAME_SUFFIX} file.")
        }
        val unreadable = files.firstOrNull { !it.isReadable() }
        if (unreadable != null) {
            throw CommandExit(ExitCode.Input, "$kind file '$unreadable' is not readable.")
        }
    }
}
