/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import hu.bme.mit.semantifyr.cli.SMOKE_CASE
import hu.bme.mit.semantifyr.cli.runCli
import hu.bme.mit.semantifyr.cli.smokeDirectory
import hu.bme.mit.semantifyr.cli.smokeModel
import hu.bme.mit.semantifyr.cli.testModels
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.isReadable
import kotlin.io.path.writeText

class ListCommandTest {

    @Test
    suspend fun `lists the cases of a model file`() {
        val result = runCli("list", smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).contains(SMOKE_CASE)
    }

    @Test
    suspend fun `lists the cases of a model directory`() {
        val result = runCli("list", smokeDirectory.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).contains(SMOKE_CASE)
    }

    @Test
    suspend fun `accepts a library file and lists none of its cases`() {
        val library = testModels.resolve("semantic/arithmetic.oxsts")

        val result = runCli("list", "-l", library.toString(), smokeModel.toString())

        assertThat(result.statusCode).describedAs(result.output).isZero()
        assertThat(result.stdout).contains(SMOKE_CASE).doesNotContain("arithmetic")
    }

    @Test
    suspend fun `a missing model path is an input error`(@TempDir directory: Path) {
        val result = runCli("list", directory.resolve("absent.oxsts").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("absent.oxsts").contains("does not exist")
    }

    @Test
    suspend fun `a model file without the oxsts extension is an input error`() {
        val result = runCli("list", testModels.resolve("constructs/constructs.tsv").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains(".oxsts")
    }

    @Test
    suspend fun `a directory without oxsts files is an input error`(@TempDir directory: Path) {
        directory.resolve("notes.txt").writeText("no model here")

        val result = runCli("list", directory.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("contains no .oxsts file")
    }

    @Test
    suspend fun `an unreadable model file is an input error`(@TempDir directory: Path) {
        val model = smokeModel.copyTo(directory.resolve("smoke.oxsts"))
        assumeTrue(model.toFile().setReadable(false) && !model.isReadable(), "the file system cannot revoke read access")

        val result = runCli("list", model.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("is not readable")
    }

    @Test
    suspend fun `a missing library path is an input error`(@TempDir directory: Path) {
        val result = runCli("list", "-l", directory.resolve("absent").toString(), smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("Library path")
    }

    @Test
    suspend fun `a model with a syntax error is a compilation error`(@TempDir directory: Path) {
        val model = directory.resolve("broken.oxsts")
        model.writeText("package broken\n\nclass Broken {\n    var x: int :=\n}\n")

        val result = runCli("list", model.toString())

        assertThat(result.statusCode).isEqualTo(4)
        assertThat(result.stderr).contains("broken.oxsts")
    }

    @Test
    suspend fun `a model the validator rejects is a compilation error`() {
        val result = runCli("list", testModels.resolve("constructs/multiple_inheritance.oxsts").toString())

        assertThat(result.statusCode).isEqualTo(4)
        assertThat(result.stderr).contains("multiple_inheritance.oxsts")
    }

    @Test
    suspend fun `a case name that names no case is an input error`() {
        val result = runCli("list", "--case-name", "semantifyr::verification::smoke::Absent", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("semantifyr::verification::smoke::Absent")
    }

    @Test
    suspend fun `a tag filter that matches no case is an input error`() {
        val result = runCli("list", "--include-tag", "no-such-tag", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("No verification case matches")
    }
}
