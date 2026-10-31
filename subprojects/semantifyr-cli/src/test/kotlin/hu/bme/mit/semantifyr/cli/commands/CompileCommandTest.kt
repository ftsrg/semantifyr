/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class CompileCommandTest {

    @Test
    suspend fun `writes the inlined model of a single case`(@TempDir directory: Path) {
        val target = directory.resolve("smoke.inlined.oxsts")

        val result = runCli("compile", "-o", target.toString(), "-d", directory.resolve("artifacts").toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).contains("Compiling $SMOKE_CASE")
        assertThat(target.readText()).contains("CounterReachesThree")
    }

    @Test
    suspend fun `a directory output for a single case is a usage error`(@TempDir directory: Path) {
        val target = directory.resolve("out").createDirectories()

        val result = runCli("compile", "-o", target.toString(), "-d", directory.resolve("artifacts").toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("is a directory")
    }
}
