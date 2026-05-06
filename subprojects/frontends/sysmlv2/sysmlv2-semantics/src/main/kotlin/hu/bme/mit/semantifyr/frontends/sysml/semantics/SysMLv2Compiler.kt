/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.cache.GenericCache
import hu.bme.mit.semantifyr.utils.hash.sha256Hex
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class SysMLv2Compiler {

    private val logger by loggerFactory()
    private val cache = GenericCache<String, String>()

    fun compile(
        sourcePath: Path,
        outputPath: Path,
        cliPath: Path,
        sysmlLibraryPath: Path,
    ): Path {
        require(Files.isRegularFile(sourcePath)) {
            "SysMLv2 source does not exist or is not a regular file: $sourcePath"
        }
        require(Files.isRegularFile(cliPath)) {
            "SysMLv2 node CLI bundle not found at $cliPath. Build the `:sysmlv2-frontend` project or point .cliPath(...) at an existing index.js."
        }
        require(Files.exists(sysmlLibraryPath)) {
            "SysML type library not found at $sysmlLibraryPath. Point .sysmlLibraryPath(...) at the extracted `sysml.library` directory."
        }

        val key = contentKey(sourcePath, cliPath, sysmlLibraryPath)
        val output = if (key != null) {
            cache.getOrCompute(key) {
                runCli(sourcePath, cliPath, sysmlLibraryPath)
            }
        } else {
            logger.debug { "Could not compute content key for '$sourcePath', skipping cache" }
            runCli(sourcePath, cliPath, sysmlLibraryPath)
        }

        val parent = outputPath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(outputPath, output)
        logger.debug { "Wrote ${output.length} bytes of OXSTS to '$outputPath'" }
        return outputPath
    }

    private fun runCli(
        sourcePath: Path,
        cliPath: Path,
        sysmlLibraryPath: Path,
    ): String {
        logger.info { "Compiling SysMLv2 source '$sourcePath' via '$cliPath'" }

        val tempOutput = Files.createTempFile("semantifyr-sysmlv2-", ".oxsts")
        try {
            val builder = ProcessBuilder(
                "node",
                cliPath.toString(),
                "compile",
                sourcePath.toString(),
                sysmlLibraryPath.toString(),
                "-o",
                tempOutput.toString(),
            ).redirectErrorStream(true)

            val process = try {
                builder.start()
            } catch (ex: IOException) {
                throw IllegalStateException(
                    "Failed to launch the SysMLv2 node CLI ($cliPath). Ensure `node` is installed and on PATH, and that the CLI bundle exists.",
                    ex,
                )
            }

            val cliOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "SysMLv2 compilation of '$sourcePath' failed with exit code $exitCode:\n$cliOutput" }

            return Files.readString(tempOutput)
        } finally {
            runCatching { Files.deleteIfExists(tempOutput) }
        }
    }

    private fun contentKey(
        sourcePath: Path,
        cliPath: Path,
        sysmlLibraryPath: Path,
    ): String? {
        return runCatching {
            val sourceHash = sha256Hex(sourcePath)
            "$sourceHash|cli=${pathFingerprint(cliPath)}|lib=${pathFingerprint(sysmlLibraryPath)}"
        }.getOrNull()
    }

    private fun pathFingerprint(path: Path): String {
        val absolute = path.toAbsolutePath().normalize().toString()
        if (!Files.exists(path)) {
            return "$absolute@missing"
        }
        val lastModifiedTime = Files.getLastModifiedTime(path).toMillis()
        val size = if (Files.isRegularFile(path)) {
            Files.size(path)
        } else {
            -1L
        }
        return "$absolute@$lastModifiedTime:$size"
    }
}
