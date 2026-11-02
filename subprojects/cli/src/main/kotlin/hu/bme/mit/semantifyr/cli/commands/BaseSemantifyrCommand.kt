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
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.checkInputPath
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary
import hu.bme.mit.semantifyr.verifier.VerificationCase
import java.nio.file.Path

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
        checkInputPath("Model", model, OxstsLibrary.FILE_NAME_SUFFIX)
        for (library in libraries) {
            checkInputPath("Library", library, OxstsLibrary.FILE_NAME_SUFFIX)
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
}
