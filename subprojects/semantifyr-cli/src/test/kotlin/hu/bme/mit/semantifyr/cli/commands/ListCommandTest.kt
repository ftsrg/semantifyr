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
import org.junit.jupiter.api.Test

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
    suspend fun `rejects a model file without the oxsts extension`() {
        val result = runCli("list", testModels.resolve("constructs/constructs.tsv").toString())

        assertThat(result.statusCode).isNotZero()
        assertThat(result.stderr).contains(".oxsts")
    }
}
