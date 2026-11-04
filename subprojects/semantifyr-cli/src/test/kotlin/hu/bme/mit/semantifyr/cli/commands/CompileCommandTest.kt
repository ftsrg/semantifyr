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
import kotlin.io.path.Path
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
    suspend fun `prints the directory of the artifacts of each case`(@TempDir directory: Path) {
        val artifacts = directory.resolve("artifacts")

        val result = runCli("compile", "-o", directory.resolve("smoke.inlined.oxsts").toString(), "-d", artifacts.toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val caseDirectory = artifacts.resolve("smoke.CounterReachesThree").toAbsolutePath().normalize()
        assertThat(result.stdout.lines()).contains("  artifacts: $caseDirectory")
        assertThat(caseDirectory).isDirectory()
    }

    @Test
    suspend fun `without an artifact directory the artifacts go to a temporary directory it prints`(@TempDir directory: Path) {
        val result = runCli("compile", "-o", directory.resolve("smoke.inlined.oxsts").toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val artifactsLine = result.stdout.lines().single {
            it.startsWith("  artifacts: ")
        }
        val caseDirectory = Path(artifactsLine.removePrefix("  artifacts: "))
        try {
            assertThat(caseDirectory).isDirectory()
            assertThat(caseDirectory.parent.fileName.toString()).startsWith("semantifyr-")
        } finally {
            caseDirectory.parent.toFile().deleteRecursively()
        }
    }

    @Test
    suspend fun `a directory output for a single case is a usage error`(@TempDir directory: Path) {
        val target = directory.resolve("out").createDirectories()

        val result = runCli("compile", "-o", target.toString(), "-d", directory.resolve("artifacts").toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("is a directory")
    }
}
