/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.files

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.utils.hash.sha256Hex
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.invariantSeparatorsPathString

class ClasspathBundle(
    private val classLoader: ClassLoader,
    private val prefix: String,
    private val include: (String) -> Boolean,
) {

    private val logger by loggerFactory()

    fun extractTo(baseDirectory: Path): Path {
        val files = read()
        val target = baseDirectory.resolve(sha256Hex(files))
        if (Files.isDirectory(target)) {
            logger.debug { "Reusing classpath bundle '$prefix' at '$target'" }
            return target
        }

        Files.createDirectories(baseDirectory)
        val temporary = Files.createDirectory(baseDirectory.resolve(".tmp-${UUID.randomUUID()}"))
        try {
            for ((relativePath, content) in files) {
                val file = temporary.resolve(relativePath)
                Files.createDirectories(file.parent)
                Files.write(file, content)
            }
            moveIntoPlace(temporary, target)
        } finally {
            temporary.toFile().deleteRecursively()
        }
        logger.info { "Extracted ${files.size} file(s) of classpath bundle '$prefix' to '$target'" }
        return target
    }

    private fun read(): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        for (base in classLoader.getResources(prefix)) {
            when (base.protocol) {
                "file" -> readDirectory(Path.of(base.toURI()), files)
                "jar" -> readJar(base.openConnection() as JarURLConnection, files)
                else -> error("Unsupported protocol for classpath bundle: $base")
            }
        }
        if (files.isEmpty()) {
            logger.warn { "No classpath resources found under '$prefix'" }
        }
        return files
    }

    private fun readDirectory(baseDirectory: Path, files: MutableMap<String, ByteArray>) {
        Files.walk(baseDirectory).use { walk ->
            for (path in walk) {
                val relativePath = baseDirectory.relativize(path).invariantSeparatorsPathString
                if (Files.isRegularFile(path) && include(relativePath)) {
                    files.getOrPut(relativePath) {
                        Files.readAllBytes(path)
                    }
                }
            }
        }
    }

    private fun readJar(connection: JarURLConnection, files: MutableMap<String, ByteArray>) {
        connection.useCaches = false
        connection.jarFile.use { jar ->
            for (entry in jar.entries()) {
                if (entry.isDirectory || !entry.name.startsWith("$prefix/")) {
                    continue
                }
                val relativePath = entry.name.removePrefix("$prefix/")
                if (include(relativePath)) {
                    files.getOrPut(relativePath) {
                        jar.getInputStream(entry).use {
                            it.readAllBytes()
                        }
                    }
                }
            }
        }
    }
}
