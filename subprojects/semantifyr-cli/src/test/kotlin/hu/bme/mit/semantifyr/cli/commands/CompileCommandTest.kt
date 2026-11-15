/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import hu.bme.mit.semantifyr.cli.writeSameStemModels
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isWritable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
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
        val caseDirectory = artifacts.resolve("smoke.semantifyr.verification.smoke.CounterReachesThree").toAbsolutePath().normalize()
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

    @Test
    suspend fun `several cases are written to portable file names that match their artifact directories`(@TempDir directory: Path) {
        val output = directory.resolve("inlined")
        val artifacts = directory.resolve("artifacts")

        val result = runCli("compile", "-o", output.toString(), "-d", artifacts.toString(), "--include-tag", "expect-fail", testModels.resolve("semantic/local_vars.oxsts").toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val files = output.listDirectoryEntries().map {
            it.name
        }
        assertThat(files).hasSizeGreaterThan(1).allMatch {
            it.matches(Regex("[A-Za-z0-9._-]+\\.oxsts"))
        }
        val directories = artifacts.listDirectoryEntries().map {
            "${it.name}.oxsts"
        }
        assertThat(files).containsExactlyInAnyOrderElementsOf(directories)
    }

    @Test
    suspend fun `cases of models sharing a file stem get their own files and directories`(@TempDir directory: Path) {
        val models = writeSameStemModels(directory)
        val output = directory.resolve("inlined")
        val artifacts = directory.resolve("artifacts")

        val result = runCli("compile", "-o", output.toString(), "-d", artifacts.toString(), models.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(output.listDirectoryEntries()).hasSize(2)
        assertThat(artifacts.listDirectoryEntries()).hasSize(2)
    }

    @Test
    suspend fun `the compilation step artifact writes the model after each pass`(@TempDir directory: Path) {
        val artifacts = directory.resolve("artifacts")

        val result = runCli("compile", "--artifacts", "none", "--enable-artifact", "compilation-step", "-o", directory.resolve("smoke.inlined.oxsts").toString(), "-d", artifacts.toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        val steps = artifacts.listDirectoryEntries().single().resolve("pipeline/steps")
        assertThat(steps).isDirectory()
        assertThat(steps.listDirectoryEntries()).isNotEmpty()
    }

    @Test
    suspend fun `the output model is no artifact kind`(@TempDir directory: Path) {
        val result = runCli("compile", "--enable-artifact", "output-model", "-o", directory.resolve("smoke.inlined.oxsts").toString(), "-d", directory.resolve("artifacts").toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("output-model")
    }

    @Test
    suspend fun `an artifact directory that cannot be written exits with the output code before compiling`(@TempDir directory: Path) {
        val readOnly = directory.resolve("read-only").createDirectories()
        assumeTrue(readOnly.toFile().setWritable(false) && !readOnly.isWritable(), "the file system cannot revoke write access")
        val target = directory.resolve("smoke.inlined.oxsts")

        val result = runCli("compile", "-o", target.toString(), "-d", readOnly.resolve("artifacts").toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isEqualTo(10)
        assertThat(result.stdout).isEmpty()
        assertThat(result.stderr).contains("Artifact directory").contains("cannot be written")
        assertThat(target.exists()).isFalse()
    }
}
