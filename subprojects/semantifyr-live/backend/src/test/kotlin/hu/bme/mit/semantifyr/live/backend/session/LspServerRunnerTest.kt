/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path as asPath

class LspServerRunnerTest {

    private val flavor = Flavor(
        id = "oxsts",
        displayName = "Semantifyr",
        binaryRelativePath = asPath("does-not-exist"),
        fileName = "snippet.oxsts",
        languageId = "oxsts",
        workspaceLayout = WorkspaceLayout.SingleFile,
        verificationCommand = null,
    )

    @Test
    fun `run fails fast when the LSP binary does not exist`(@TempDir tmpDir: Path) = runTest {
        val runner = LspServerRawRunner(flavor, tmpDir.resolve("ws"), configWith(tmpDir))

        val exception = runCatching { runner.run() }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception?.message).contains("LSP binary")
    }

    @Test
    fun `workspace is cleaned up when startup fails`(@TempDir tmpDir: Path) = runTest {
        val workDir = tmpDir.resolve("ws")
        val runner = LspServerRawRunner(flavor, workDir, configWith(tmpDir))

        runCatching { runner.run() }

        // Startup failure still cleans up the workspace.
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
