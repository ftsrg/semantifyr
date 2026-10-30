/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.files.ClasspathBundle
import hu.bme.mit.semantifyr.utils.files.moveIntoPlace
import hu.bme.mit.semantifyr.utils.hash.sha256Hex
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString

class SysMLv2Compiler(
    private val cacheDirectory: Path = Path.of(System.getProperty("user.home"), ".semantifyr", "sysml-cache"),
) {

    private val logger by loggerFactory()

    private val bundleDir: Path by lazy { extractBundle() }
    private val cliPath: Path by lazy { bundleDir.resolve("cli.cjs") }
    private val sysmlLibraryPath: Path by lazy { bundleDir.resolve("sysml.library") }

    fun compile(sourcePath: Path, outputPath: Path): Path {
        require(Files.isRegularFile(sourcePath)) {
            "SysMLv2 source does not exist or is not a regular file: $sourcePath"
        }

        val entry = cacheEntry(sourcePath)
        if (Files.isRegularFile(entry)) {
            logger.info { "Reusing cached OXSTS '$entry' for SysMLv2 source '$sourcePath'" }
        } else {
            runCli(sourcePath, entry)
        }

        Files.createDirectories(outputPath.toAbsolutePath().parent)
        val temporary = outputPath.resolveSibling(".tmp-${UUID.randomUUID()}.oxsts")
        try {
            Files.copy(entry, temporary)
            Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
        logger.debug { "Copied cached OXSTS '$entry' to '$outputPath'" }
        return outputPath
    }

    private fun cacheEntry(sourcePath: Path): Path {
        // the CLI builds every SysML and KerML file under the model's directory as its workspace
        val workspace = sourcePath.toAbsolutePath().parent
        val files = Files.walk(workspace).use { walk ->
            walk.filter {
                Files.isRegularFile(it) && it.extension in WORKSPACE_EXTENSIONS
            }.toList().associate {
                workspace.relativize(it).invariantSeparatorsPathString to Files.readAllBytes(it)
            }
        }
        return cacheDirectory
            .resolve(bundleDir.fileName.toString())
            .resolve("${sourcePath.fileName}-${sha256Hex(files)}.oxsts")
    }

    private fun runCli(sourcePath: Path, entry: Path) {
        logger.info { "Compiling SysMLv2 source '$sourcePath' via bundled CLI '$cliPath'" }

        Files.createDirectories(entry.parent)
        val temporary = entry.resolveSibling(".tmp-${UUID.randomUUID()}.oxsts")
        try {
            val builder = ProcessBuilder(
                "node",
                cliPath.toString(),
                "compile",
                sourcePath.toString(),
                sysmlLibraryPath.toString(),
                "-o",
                temporary.toString(),
            ).redirectErrorStream(true)

            val process = try {
                builder.start()
            } catch (ex: IOException) {
                throw IllegalStateException(
                    "Failed to launch the SysMLv2 node CLI ($cliPath). Ensure `node` is installed and on PATH.",
                    ex,
                )
            }

            val cliOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "SysMLv2 compilation of '$sourcePath' failed with exit code $exitCode:\n$cliOutput"
            }

            moveIntoPlace(temporary, entry)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
    }

    private fun extractBundle(): Path {
        val bundle = ClasspathBundle(SysMLv2Compiler::class.java.classLoader, BUNDLE_PREFIX) { true }
        val target = bundle.extractTo(Path.of(System.getProperty("user.home"), ".semantifyr", "sysml-cli"))
        check(Files.isRegularFile(target.resolve("cli.cjs"))) {
            "Bundled SysML CLI not found on classpath at '$BUNDLE_PREFIX/'"
        }
        return target
    }

    companion object {
        private const val BUNDLE_PREFIX = "sysml-cli"
        private val WORKSPACE_EXTENSIONS = setOf("sysml", "kerml")
    }
}
