/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.utils.cache.GenericCache
import hu.bme.mit.semantifyr.utils.hash.sha256Hex
import java.io.IOException
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path

class SysMLv2Compiler {

    private val logger by loggerFactory()
    private val cache = GenericCache<String, String>()

    private val bundleDir: Path by lazy { extractBundle() }
    private val cliPath: Path by lazy { bundleDir.resolve("index.js") }
    private val sysmlLibraryPath: Path by lazy { bundleDir.resolve("sysml.library") }

    fun compile(sourcePath: Path, outputPath: Path): Path {
        require(Files.isRegularFile(sourcePath)) {
            "SysMLv2 source does not exist or is not a regular file: $sourcePath"
        }

        val key = contentKey(sourcePath)
        val output = if (key != null) {
            cache.getOrCompute(key) { runCli(sourcePath) }
        } else {
            logger.debug { "Could not compute content key for '$sourcePath', skipping cache" }
            runCli(sourcePath)
        }

        val parent = outputPath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(outputPath, output)
        logger.debug { "Wrote ${output.length} bytes of OXSTS to '$outputPath'" }
        return outputPath
    }

    private fun runCli(sourcePath: Path): String {
        logger.info { "Compiling SysMLv2 source '$sourcePath' via bundled CLI '$cliPath'" }

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
                    "Failed to launch the SysMLv2 node CLI ($cliPath). Ensure `node` is installed and on PATH.",
                    ex,
                )
            }

            val cliOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "SysMLv2 compilation of '$sourcePath' failed with exit code $exitCode:\n$cliOutput"
            }

            return Files.readString(tempOutput)
        } finally {
            runCatching { Files.deleteIfExists(tempOutput) }
        }
    }

    private fun contentKey(sourcePath: Path): String? {
        return runCatching { sha256Hex(sourcePath) }.getOrNull()
    }

    private fun extractBundle(): Path {
        val target = Path.of(System.getProperty("user.home"), ".semantifyr", "sysml-cli")
        Files.createDirectories(target)

        val classLoader = SysMLv2Compiler::class.java.classLoader
        val bases = classLoader.getResources(BUNDLE_PREFIX)
        if (!bases.hasMoreElements()) {
            error("Bundled SysML CLI not found on classpath at '$BUNDLE_PREFIX/'")
        }

        var count = 0
        while (bases.hasMoreElements()) {
            val base = bases.nextElement()
            count += when (base.protocol) {
                "file" -> extractFromDir(Path.of(base.toURI()), target)
                "jar" -> extractFromJar(base.openConnection() as JarURLConnection, target)
                else -> error("Unsupported protocol for bundled SysML CLI: $base")
            }
        }
        logger.info { "Extracted $count bundled SysML CLI file(s) to '$target'" }
        return target
    }

    private fun extractFromDir(baseDir: Path, target: Path): Int {
        var count = 0
        Files.walk(baseDir).use { walk ->
            for (path in walk) {
                if (!Files.isRegularFile(path)) {
                    continue
                }
                val relative = baseDir.relativize(path)
                writeSync(target.resolve(relative.toString()), Files.newInputStream(path))
                count++
            }
        }
        return count
    }

    private fun extractFromJar(connection: JarURLConnection, target: Path): Int {
        val prefix = "$BUNDLE_PREFIX/"
        val jar = connection.jarFile
        val entries = jar.entries()
        var count = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) {
                continue
            }
            val relative = entry.name.substring(prefix.length)
            jar.getInputStream(entry).use {
                writeSync(target.resolve(relative), it)
            }
            count++
        }
        return count
    }

    private fun writeSync(out: Path, stream: InputStream) {
        val parent = out.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val desired = stream.readAllBytes()
        if (Files.exists(out) && Files.size(out) == desired.size.toLong() && Files.readAllBytes(out).contentEquals(desired)) {
            return
        }
        Files.write(out, desired)
    }

    companion object {
        private const val BUNDLE_PREFIX = "sysml-cli"
    }
}
