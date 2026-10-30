/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.invariantSeparatorsPathString

class ClasspathBundleTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    private val baseDirectory: Path
        get() = temporaryDirectory.resolve("extracted")

    private fun directoryLoader(files: Map<String, String>): URLClassLoader {
        val root = Files.createTempDirectory(temporaryDirectory, "classes")
        for ((name, content) in files) {
            val file = root.resolve(name)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        return URLClassLoader(arrayOf(root.toUri().toURL()), null)
    }

    private fun jarLoader(files: Map<String, String>): URLClassLoader {
        val jar = Files.createTempFile(temporaryDirectory, "classes", ".jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            val directories = files.keys.flatMap { name ->
                val segments = name.split('/').dropLast(1)
                segments.indices.map {
                    segments.take(it + 1).joinToString("/")
                }
            }.distinct()
            for (directory in directories) {
                output.putNextEntry(JarEntry("$directory/"))
                output.closeEntry()
            }
            for ((name, content) in files) {
                output.putNextEntry(JarEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return URLClassLoader(arrayOf(jar.toUri().toURL()), null)
    }

    private fun textBundle(classLoader: ClassLoader): ClasspathBundle {
        return ClasspathBundle(classLoader, "bundle") {
            it.endsWith(".txt")
        }
    }

    private fun contentsOf(directory: Path): Map<String, String> {
        Files.walk(directory).use { walk ->
            return walk.filter {
                Files.isRegularFile(it)
            }.toList().associate {
                directory.relativize(it).invariantSeparatorsPathString to Files.readString(it)
            }
        }
    }

    @Test
    fun `extracts the included files from a directory`() {
        directoryLoader(MIXED_FILES).use {
            val target = textBundle(it).extractTo(baseDirectory)
            assertThat(contentsOf(target)).isEqualTo(INCLUDED_FILES)
        }
    }

    @Test
    fun `extracts the included files from a jar`() {
        jarLoader(MIXED_FILES).use {
            val target = textBundle(it).extractTo(baseDirectory)
            assertThat(contentsOf(target)).isEqualTo(INCLUDED_FILES)
        }
    }

    @Test
    fun `same content reuses the existing directory`() {
        val first = directoryLoader(MIXED_FILES).use {
            textBundle(it).extractTo(baseDirectory)
        }
        Files.writeString(first.resolve("marker"), "kept")

        val second = jarLoader(MIXED_FILES).use {
            textBundle(it).extractTo(baseDirectory)
        }

        assertThat(second).isEqualTo(first)
        assertThat(second.resolve("marker")).hasContent("kept")
    }

    @Test
    fun `changed content extracts to a new directory without dropped files`() {
        val first = directoryLoader(MIXED_FILES).use {
            textBundle(it).extractTo(baseDirectory)
        }
        val second = directoryLoader(mapOf("bundle/a.txt" to "changed")).use {
            textBundle(it).extractTo(baseDirectory)
        }

        assertThat(second).isNotEqualTo(first)
        assertThat(contentsOf(second)).isEqualTo(mapOf("a.txt" to "changed"))
        assertThat(contentsOf(first)).isEqualTo(INCLUDED_FILES)
    }

    @Test
    fun `concurrent extractions agree on one complete directory`() {
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val targets = directoryLoader(MIXED_FILES).use { loader ->
            Executors.newFixedThreadPool(threads).use { executor ->
                val tasks = List(threads) {
                    Callable {
                        barrier.await()
                        textBundle(loader).extractTo(baseDirectory)
                    }
                }
                executor.invokeAll(tasks).map {
                    it.get()
                }
            }
        }

        assertThat(targets.distinct()).hasSize(1)
        assertThat(contentsOf(targets.first())).isEqualTo(INCLUDED_FILES)
        Files.list(baseDirectory).use {
            assertThat(it.toList()).containsExactly(targets.first())
        }
    }

    companion object {
        private val MIXED_FILES = mapOf(
            "bundle/a.txt" to "a",
            "bundle/nested/b.txt" to "b",
            "bundle/skipped.bin" to "skipped",
            "other/c.txt" to "c",
        )

        private val INCLUDED_FILES = mapOf(
            "a.txt" to "a",
            "nested/b.txt" to "b",
        )
    }
}
