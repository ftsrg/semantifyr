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

class SysMLCompileCommandTest {

    @Test
    suspend fun `a missing model is an input error`(@TempDir directory: Path) {
        val result = runSysMLCli("compile", directory.resolve("absent.sysml").toString())

        assertThat(result.statusCode).isEqualTo(3)
        assertThat(result.stderr).contains("does not exist")
    }
}
