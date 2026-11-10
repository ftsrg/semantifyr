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
import hu.bme.mit.semantifyr.cli.common.CommandExit
import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.cli.common.options.ArtifactOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.CompilationOptionGroup
import hu.bme.mit.semantifyr.cli.common.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.caseDirectory
import hu.bme.mit.semantifyr.verifier.caseDirectoryName
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension

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
        .help("Output file, or directory for several cases.")

    override fun help(context: Context): String {
        return "Compile cases to inlined OXSTS."
    }

    override suspend fun run() {
        logger.info { "compile model=$model libraries=$libraries" }

        checkInputs()
        artifactOptions.checkOutputDirectory()
        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)

        val targets = if (cases.size == 1) {
            listOf(validateSingleCaseOutput())
        } else {
            val directory = validateMultipleCasesOutput(cases.size)
            cases.map {
                directory.resolve("${caseDirectoryName(it)}.oxsts")
            }
        }
        val compiler = SemantifyrCompiler(injector, artifactOptions.resolved, compilationOptions.resolved)
        for ((case, target) in cases.zip(targets)) {
            writeCase(compiler, case, target)
        }
    }

    private fun validateSingleCaseOutput(): Path {
        val target = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.inlined.oxsts")
        if (Files.isDirectory(target)) {
            throw CommandExit(ExitCode.Usage, "--output '$target' is a directory, but only one case is selected. Provide a file path or omit the flag.")
        }
        return target
    }

    private fun validateMultipleCasesOutput(caseCount: Int): Path {
        val directory = outputPath ?: model.resolveSibling("${model.nameWithoutExtension}.inlined")
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw CommandExit(ExitCode.Usage, "--output '$directory' is a file, but $caseCount cases are selected. Provide a directory or omit the flag.")
        }
        Files.createDirectories(directory)
        return directory
    }

    private fun writeCase(
        compiler: SemantifyrCompiler,
        case: VerificationCase,
        target: Path,
    ) {
        echo("Compiling ${case.qualifiedName} to $target")
        val caseDirectory = caseDirectory(artifactOptions.resolvedOutputDirectory, case)
        val compiled = compileCase(compiler, case, caseDirectory)
        writeInlinedOxsts(compiled.inlinedOxsts, target)
        if (caseDirectory.isDirectory()) {
            echo("  artifacts: ${caseDirectory.toAbsolutePath().normalize()}")
        }
    }

    private fun writeInlinedOxsts(
        inlinedOxsts: InlinedOxsts,
        target: Path,
    ) {
        Files.createDirectories(target.toAbsolutePath().parent)
        target.bufferedWriter().use {
            serializer.serialize(inlinedOxsts, it, SaveOptions.defaultOptions())
        }
    }
}
