/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.testing.testSessionContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LspServerRunnerTest {

    @Test
    suspend fun `run fails fast when the LSP binary does not exist`(@TempDir tmpDir: Path) {
        val runner = LspServerRawRunner(testSessionContext(tmpDir.resolve("ws")), configWith(tmpDir))

        val exception = runCatching { runner.run() }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception?.message).contains("LSP binary")
    }

    @Test
    suspend fun `workspace is cleaned up when startup fails`(@TempDir tmpDir: Path) {
        val workDir = tmpDir.resolve("ws")
        val runner = LspServerRawRunner(testSessionContext(workDir), configWith(tmpDir))

        runCatching { runner.run() }

        assertThat(workDir.toFile().exists()).isFalse()
    }

    private fun configWith(tmpDir: Path): BackendConfig {
        return BackendConfig(
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = tmpDir.toString(),
                lspBinariesDirectory = tmpDir.resolve("bin").toString(),
            ),
        )
    }
}
