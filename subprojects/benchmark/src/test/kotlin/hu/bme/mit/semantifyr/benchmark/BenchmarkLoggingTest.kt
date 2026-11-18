/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

class BenchmarkLoggingTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the classes of the benchmark source sets log at info and the classes of other entries do not`() {
        val ownSourceSet = directory.resolve("benchmark-classes")
        ownSourceSet.resolve("example/benchmarks/PhasesBenchmark.class").createParentDirectories().createFile()
        val otherModuleFeature = directory.resolve("compiler-benchmark.jar")
        JarOutputStream(otherModuleFeature.outputStream()).use {
            it.putNextEntry(JarEntry("example/blocks/CaseCompilation.class"))
            it.closeEntry()
        }
        val product = directory.resolve("main-classes")
        product.resolve("example/blocks/Compiler.class").createParentDirectories().createFile()

        logBenchmarkClassesAtInfo(
            listOf(
                ownSourceSet,
                otherModuleFeature,
            ),
        )

        assertThat(LogManager.getLogger("example.benchmarks.PhasesBenchmark").isInfoEnabled).isTrue()
        assertThat(LogManager.getLogger("example.blocks.CaseCompilation").isInfoEnabled).isTrue()
        assertThat(LogManager.getLogger("example.blocks.Compiler").isInfoEnabled).isFalse()
    }
}
