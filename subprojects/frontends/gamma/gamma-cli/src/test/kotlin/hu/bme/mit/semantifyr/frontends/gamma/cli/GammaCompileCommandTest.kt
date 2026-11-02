/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.readText

class GammaCompileCommandTest {

    @Test
    suspend fun `writes the OXSTS model next to the Gamma model`(@TempDir directory: Path) {
        val model = simpleModel.copyTo(directory.resolve("Simple.gamma"))

        val result = runGammaCli("compile", model.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val target = directory.resolve("Simple.oxsts")
        assertThat(result.stdout).isEqualTo("Compiling $model to $target\n")
        assertThat(target.readText()).contains("WorkerStatechartIdleReachable")
    }

    @Test
    suspend fun `writes the OXSTS model to the output path`(@TempDir directory: Path) {
        val target = directory.resolve("out").resolve("Simple.oxsts")

        val result = runGammaCli("compile", "-o", target.toString(), simpleModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(target.readText()).contains("WorkerStatechartIdleReachable")
    }

    @Test
    suspend fun `a missing model is an input error`(@TempDir directory: Path) {
        val result = runGammaCli("compile", directory.resolve("Absent.gamma").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("does not exist")
    }
}
