/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SysMLv2CompilerTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    private val workspace: Path
        get() = temporaryDirectory.resolve("workspace")

    private val cacheDirectory: Path
        get() = temporaryDirectory.resolve("cache")

    private fun writeModel(name: String, content: String): Path {
        Files.createDirectories(workspace)
        return Files.writeString(workspace.resolve(name), content)
    }

    private fun cacheEntries(): List<Path> {
        if (!Files.exists(cacheDirectory)) {
            return emptyList()
        }
        Files.walk(cacheDirectory).use { walk ->
            return walk.filter {
                Files.isRegularFile(it)
            }.toList()
        }
    }

    @Test
    fun `cache entry is shared across instances until a workspace file changes`() {
        val model = writeModel("door.sysml", "part def Door;\n")

        SysMLv2Compiler(cacheDirectory).compile(model, temporaryDirectory.resolve("first.oxsts"))
        Files.writeString(cacheEntries().single(), "cached")

        val second = SysMLv2Compiler(cacheDirectory).compile(model, temporaryDirectory.resolve("second.oxsts"))
        assertThat(second).hasContent("cached")

        writeModel("window.sysml", "part def Window;\n")
        val third = SysMLv2Compiler(cacheDirectory).compile(model, temporaryDirectory.resolve("third.oxsts"))
        assertThat(Files.readString(third)).contains("class Door")
        assertThat(cacheEntries()).hasSize(2)
    }

    @Test
    fun `failed compilation leaves no cache entry`() {
        val model = writeModel("broken.sysml", "part def Door {\n")

        assertThatThrownBy {
            SysMLv2Compiler(cacheDirectory).compile(model, temporaryDirectory.resolve("broken.oxsts"))
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(cacheEntries()).isEmpty()
    }
}
