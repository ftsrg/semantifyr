/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SemantifyrCommandTest {

    @Test
    suspend fun `the root command prints its version`() {
        val result = runCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr version ")
    }

    @Test
    suspend fun `help exits with success`() {
        val result = runCli("verify", "--help")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).contains("Usage: semantifyr verify")
    }

    @Test
    suspend fun `no command is a usage error`() {
        val result = runCli()

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("Usage: semantifyr")
    }

    @Test
    suspend fun `a command without its model path is a usage error`() {
        val result = runCli("list")

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stdout).isEmpty()
        assertThat(result.stderr).contains("Usage: semantifyr list")
    }

    @Test
    suspend fun `an unknown command is a usage error`() {
        val result = runCli("prove", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("prove")
    }

    @Test
    suspend fun `an unknown option is a usage error`() {
        val result = runCli("list", "--no-such-option", smokeModel.toString())

        assertThat(result.statusCode).isEqualTo(2)
        assertThat(result.stderr).contains("--no-such-option")
    }
}
