/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.cli.commands.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.qualifiedNameToDirectoryName
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess

class CompileCommand @Inject constructor(
    semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
    private val serializer: ISerializer,
    private val injector: Injector,
) : BaseSemantifyrCommand("compile", semantifyrLoader) {
    private val logger by loggerFactory()

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)
    private val compilationOptions by CompilationOptionGroup()
    private val artifactOptions by ArtifactOptionGroup()

    private val outputPath by option("-o", "--output")
        .path(mustExist = false, canBeFile = true, canBeDir = true)
        .help("Where to write the inlined OXSTS. With a single selected case: a file path; with multiple: a directory holding `<case-qualified-name>.oxsts`. Defaults to a sibling of the input model.")

    override fun help(context: Context): String {
        return "Run the inlining pipeline for one or more verification cases and write the inlined OXSTS."
    }

    override suspend fun run() {
        logger.info { "compile model=$model libraries=$libraries" }

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)
        if (cases.isEmpty()) {
            echo("No verification cases match the given filter.", err = true)
            exitProcess(1)
        }

        if (cases.size == 1) {
            val target = validateSingleCaseOutput()
            compileSingleCase(cases.single(), target)
        } else {
            val directory = validateMultipleCasesOutput(cases.size)
            compileMultipleCases(cases, directory)
        }
    }

    private fun validateSingleCaseOutput(): Path {
        val target = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.inlined.oxsts")
        if (Files.isDirectory(target)) {
            echo("--output '$target' is a directory, but only one case is selected. Provide a file path or omit the flag.", err = true)
            exitProcess(2)
        }
        return target
    }

    private fun validateMultipleCasesOutput(caseCount: Int): Path {
        val directory = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.inlined")
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            echo("--output '$directory' is a file, but $caseCount cases are selected. Provide a directory or omit the flag.", err = true)
            exitProcess(2)
        }
        Files.createDirectories(directory)
        return directory
    }

    private fun compileSingleCase(
        case: VerificationCase,
        target: Path,
    ) {
        runCompiler { compiler ->
            echo("Compiling ${case.qualifiedName} to $target")
            val caseDirectory = artifactOptions.resolvedOutputDirectory.resolve(qualifiedNameToDirectoryName(case.qualifiedName))
            val compiled = compiler.compile(case.classDeclaration, caseDirectory)
            writeInlinedOxsts(compiled.inlinedOxsts, target)
        }
    }

    private fun compileMultipleCases(
        cases: List<VerificationCase>,
        directory: Path,
    ) {
        runCompiler { compiler ->
            for (case in cases) {
                val target = directory.resolve("${case.qualifiedName}.oxsts")
                echo("Compiling ${case.qualifiedName} to $target")
                val caseDirectory = artifactOptions.resolvedOutputDirectory.resolve(qualifiedNameToDirectoryName(case.qualifiedName))
                val compiled = compiler.compile(case.classDeclaration, caseDirectory)
                writeInlinedOxsts(compiled.inlinedOxsts, target)
            }
        }
    }

    private inline fun runCompiler(block: (SemantifyrCompiler) -> Unit) {
        val compiler = SemantifyrCompiler(injector, artifactOptions.resolved, compilationOptions.resolved)
        block(compiler)
    }

    private fun writeInlinedOxsts(
        inlinedOxsts: InlinedOxsts,
        target: Path,
    ) {
        Files.createDirectories(target.parent)
        target.bufferedWriter().use {
            serializer.serialize(inlinedOxsts, it, SaveOptions.defaultOptions())
        }
    }
}
