/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.frontends.sysml.wrapper.SysMLv2Compiler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.createFile

class SysMLCompileCommandTest {

    private val compiler = mock<SysMLv2Compiler>()

    @Test
    suspend fun `the translation goes next to the model in the primary extension`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml").createFile()
        val target = directory.resolve("room.semantifyr")

        val result = runSysMLCli("compile", model.toString(), compiler = compiler)

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).isEqualTo("Compiling $model to $target\n")
        verify(compiler).compile(model, target)
    }

    @Test
    suspend fun `the translation goes to the output path`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml").createFile()
        val target = directory.resolve("out").resolve("translated.semantifyr")

        val result = runSysMLCli("compile", "-o", target.toString(), model.toString(), compiler = compiler)

        assertThat(result.statusCode).describedAs(result.output).isZero()
        verify(compiler).compile(model, target)
    }

    @Test
    suspend fun `a failed translation is a compilation error`(@TempDir directory: Path) {
        val model = directory.resolve("room.sysml").createFile()
        whenever(compiler.compile(any(), any())).thenThrow(IllegalStateException("the model does not translate"))

        val result = runSysMLCli("compile", model.toString(), compiler = compiler)

        assertThat(result.statusCode).isEqualTo(ExitCode.Compilation.code)
        assertThat(result.stderr).contains("the model does not translate")
    }

    @Test
    suspend fun `a missing model is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("compile", directory.resolve("absent.sysml").toString(), compiler = compiler)

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("does not exist")
    }
}
