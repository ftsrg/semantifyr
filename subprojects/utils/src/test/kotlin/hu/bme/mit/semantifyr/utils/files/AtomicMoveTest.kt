/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.utils.files

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class AtomicMoveTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    private fun directoryWith(name: String, content: String): Path {
        val directory = Files.createDirectory(temporaryDirectory.resolve(name))
        Files.writeString(directory.resolve("file"), content)
        return directory
    }

    @Test
    fun `moves into a free target`() {
        val source = directoryWith("source", "moved")
        val target = temporaryDirectory.resolve("target")

        moveIntoPlace(source, target)

        assertThat(target.resolve("file")).hasContent("moved")
        assertThat(source).doesNotExist()
    }

    @Test
    fun `a directory placed by a concurrent writer is kept`() {
        val target = directoryWith("target", "first")
        val source = directoryWith("source", "second")

        moveIntoPlace(source, target)

        assertThat(target.resolve("file")).hasContent("first")
    }

    @Test
    fun `a failure without a target is rethrown`() {
        val source = directoryWith("source", "moved")
        val target = temporaryDirectory.resolve("missing").resolve("target")

        assertThatThrownBy {
            moveIntoPlace(source, target)
        }.isInstanceOf(IOException::class.java)
    }
}
