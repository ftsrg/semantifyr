/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class SysMLCompileCommandTest {

    @Test
    suspend fun `writes the OXSTS translation next to the model`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = tutorialModel.copyTo(directory.resolve("model").createDirectories().resolve("room.sysml"))

        val result = runSysMLCli("compile", model.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val target = model.resolveSibling("room.oxsts")
        assertThat(result.stdout).isEqualTo("Compiling $model to $target\n")
        assertThat(target.readText()).contains("LampLitReachable")
    }

    @Test
    suspend fun `writes the OXSTS translation to the output path`(@TempDir directory: Path) {
        assumeNodeOnPath()
        val model = tutorialModel.copyTo(directory.resolve("model").createDirectories().resolve("room.sysml"))
        val target = directory.resolve("out").resolve("translated.oxsts")

        val result = runSysMLCli("compile", "-o", target.toString(), model.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(target.readText()).contains("LampLitReachable")
    }

    @Test
    suspend fun `a missing model is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("compile", directory.resolve("absent.sysml").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("does not exist")
    }
}
