/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class InputsTest {

    private val extensions = listOf("first", "second", "third")

    @Test
    fun `a file of any of the extensions is an input`(@TempDir directory: Path) {
        for (extension in extensions) {
            val file = directory.resolve("model.$extension")
            file.writeText("model")

            assertThatCode {
                checkInputPath("Model", file, extensions)
            }.doesNotThrowAnyException()
        }
    }

    @Test
    fun `a directory holding a file of any one of the extensions is an input`(@TempDir directory: Path) {
        for (extension in extensions) {
            val inner = directory.resolve(extension).createDirectories()
            inner.resolve("model.$extension").writeText("model")

            assertThatCode {
                checkInputPath("Model", inner, extensions)
            }.doesNotThrowAnyException()
        }
    }

    @Test
    fun `a file of another kind is refused with a message naming every extension`(@TempDir directory: Path) {
        val file = directory.resolve("notes.txt")
        file.writeText("notes")

        assertThatThrownBy {
            checkInputPath("Model", file, extensions)
        }.isInstanceOfSatisfying(CommandExit::class.java) {
            assertThat(it.statusCode).isEqualTo(ExitCode.Input.code)
            assertThat(it.message).endsWith("a file ending in .first, .second or .third.")
        }
    }

    @Test
    fun `a directory without a file of any of the extensions is refused with a message naming every extension`(@TempDir directory: Path) {
        directory.resolve("notes.txt").writeText("notes")

        assertThatThrownBy {
            checkInputPath("Model", directory, extensions)
        }.isInstanceOfSatisfying(CommandExit::class.java) {
            assertThat(it.message).endsWith("contains no .first, .second or .third file.")
        }
    }

    @Test
    fun `a file of another kind is refused with a message naming the one extension`(@TempDir directory: Path) {
        val file = directory.resolve("notes.txt")
        file.writeText("notes")

        assertThatThrownBy {
            checkInputPath("Model", file, listOf("first"))
        }.isInstanceOfSatisfying(CommandExit::class.java) {
            assertThat(it.message).endsWith("neither a directory nor a file ending in .first.")
        }
    }
}
