/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.server.Flavor
import hu.bme.mit.semantifyr.live.backend.server.WorkspaceLayout
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
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
        verificationCommand = "oxsts.case.verify",
        discoveryCommand = "oxsts.case.discover",
    )

    @Test
    fun `run fails fast when the LSP binary does not exist`(@TempDir tmpDir: Path) = runTest {
        val runner = LspServerRawRunner(contextWith(tmpDir.resolve("ws")), configWith(tmpDir))

        val exception = runCatching { runner.run() }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception?.message).contains("LSP binary")
    }

    @Test
    fun `workspace is cleaned up when startup fails`(@TempDir tmpDir: Path) = runTest {
        val workDir = tmpDir.resolve("ws")
        val runner = LspServerRawRunner(contextWith(workDir), configWith(tmpDir))

        runCatching { runner.run() }

        // Startup failure still cleans up the workspace.
        assertThat(workDir.toFile().exists()).isFalse()
    }

    private fun contextWith(workDir: Path): SessionContext {
        return SessionContext(
            sessionId = "test-session",
            remoteIp = "127.0.0.1",
            flavor = flavor,
            webSocketSession = mock<WebSocketSession>(),
            workingDirectoryPath = workDir,
        )
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
