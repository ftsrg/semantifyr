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

class SysMLVerifyCommandTest {

    @Test
    suspend fun `an OXSTS model is an input error`() {
        val result = runSysMLCli("verify", semanticModels.resolve("arithmetic.semantifyr").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("arithmetic.semantifyr").contains(".sysml")
    }

    @Test
    suspend fun `a model directory is an input error`() {
        val result = runSysMLCli("verify", tutorialModel.parent.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains(".sysml")
    }

    @Test
    suspend fun `a missing library path is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("verify", "-l", directory.resolve("absent").toString(), tutorialModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("Library path")
    }

    @Test
    suspend fun `a library of another kind is an input error naming the extensions of OXSTS`() {
        val result = runSysMLCli("verify", "-l", tutorialModel.toString(), tutorialModel.toString())

        assertThat(result.statusCode).isEqualTo(3)
        for (extension in oxstsExtensions) {
            assertThat(result.stderr).contains(".$extension")
        }
    }

    @Test
    suspend fun `the root command names its launcher`() {
        val result = runSysMLCli("--version")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).startsWith("semantifyr-sysml version ")
    }

    @Test
    suspend fun `the launcher can list the portfolios`() {
        val result = runSysMLCli("--help")

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).containsPattern("\\n\\s+portfolios\\s+List the portfolios")
    }
}
