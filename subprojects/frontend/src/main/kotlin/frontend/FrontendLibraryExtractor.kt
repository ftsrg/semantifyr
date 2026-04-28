/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontend

import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

object FrontendLibraryExtractor {

    private val cache = ConcurrentHashMap<CacheKey, List<Path>>()

    fun extractClasspath(
        classLoader: ClassLoader,
        resourcePrefix: String,
    ): List<Path> {
        val key = CacheKey(classLoader, resourcePrefix.trim('/'))
        cache[key]?.let { return it }
        synchronized(cache) {
            cache[key]?.let { return it }
            val target = Files.createTempDirectory("semantifyr-frontend-lib-")
            target.toFile().deleteOnExit()
            val extracted = extractClasspath(classLoader, resourcePrefix, target)
            cache[key] = extracted
            return extracted
        }
    }

    private data class CacheKey(
        val classLoader: ClassLoader,
        val resourcePrefix: String,
    )

    fun extractClasspath(
        classLoader: ClassLoader,
        resourcePrefix: String,
        target: Path,
    ): List<Path> {
        Files.createDirectories(target)
        val prefix = resourcePrefix.trim('/')
        val extracted = mutableListOf<Path>()

        val bases = classLoader.getResources(prefix).toList()
        for (base in bases) {
            when (base.protocol) {
                "file" -> extractFromDir(
                    baseDir = Path.of(base.toURI()),
                    prefix = prefix,
                    target = target,
                    extracted = extracted,
                )
                "jar" -> extractFromJar(
                    connection = base.openConnection() as JarURLConnection,
                    prefix = prefix,
                    target = target,
                    extracted = extracted,
                )
                else -> error("Unsupported resource URL protocol for frontend libraries: $base")
            }
        }
        return extracted
    }

    private fun extractFromDir(
        baseDir: Path,
        prefix: String,
        target: Path,
        extracted: MutableList<Path>,
    ) {
        Files.walk(baseDir).use { stream ->
            for (path in stream) {
                if (Files.isDirectory(path)) continue
                val relative = baseDir.relativize(path).toString()
                val destination = target.resolve(relative)
                Files.createDirectories(destination.parent)
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                extracted.add(destination)
            }
        }
    }

    private fun extractFromJar(
        connection: JarURLConnection,
        prefix: String,
        target: Path,
        extracted: MutableList<Path>,
    ) {
        val jar = connection.jarFile
        val entries = jar.entries()
        val normalizedPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            if (!entry.name.startsWith(normalizedPrefix)) continue
            val relative = entry.name.removePrefix(normalizedPrefix)
            val destination = target.resolve(relative)
            Files.createDirectories(destination.parent)
            jar.getInputStream(entry).use { input ->
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            extracted.add(destination)
        }
    }
}
