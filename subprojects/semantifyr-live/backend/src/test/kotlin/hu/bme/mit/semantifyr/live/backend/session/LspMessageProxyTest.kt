/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.ArtifactsConfigInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerReadinessInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SemantifyrLiveMethodInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.VerificationMessageInterceptor
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.WorkspaceSyncerInterceptor
import hu.bme.mit.semantifyr.live.backend.server.LspProxyInfo
import hu.bme.mit.semantifyr.live.backend.server.SessionInfo
import hu.bme.mit.semantifyr.live.backend.testing.FakeLspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.testing.FakeLspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionControlManager
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.testing.FakeSessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.LspMessages
import hu.bme.mit.semantifyr.live.backend.testing.serialize
import hu.bme.mit.semantifyr.live.backend.testing.testLspMessageHandler
import hu.bme.mit.semantifyr.live.backend.testing.testSessionContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration

class LspMessageProxyTest {

    private val rewriter = UriRewriter(
        clientUri = "file:///workspace/",
        serverUri = "file:///tmp/session/",
    )

    private fun newProxy(
        clientConnector: LspClientRawConnector,
        serverConnector: LspServerRawConnector,
        workspace: Path,
    ): LspMessageProxy {
        val sessionContext = testSessionContext(workspace)
        val workspaceSyncer = WorkspaceSyncer(
            sessionId = sessionContext.sessionId,
            clientUri = rewriter.clientUri,
            targetFile = workspace.resolve("snippet.oxsts"),
        )
        val sessionInfo = SessionInfo(
            sessionId = sessionContext.sessionId,
            remoteIp = sessionContext.remoteIp,
            flavorId = sessionContext.flavor.id,
            uptime = Duration.ZERO,
            workingDirectory = workspace.toString(),
            activeVerifications = emptyList(),
            started = false,
            bridgeInfo = LspProxyInfo(0L, 0L, 0L, Duration.ZERO, Duration.ZERO),
        )
        return LspMessageProxy(
            serverConnector,
            clientConnector,
            rewriter,
            testLspMessageHandler,
            LspServerReadinessInterceptor(),
            WorkspaceSyncerInterceptor(workspaceSyncer),
            ArtifactsConfigInterceptor(sessionContext),
            SemantifyrLiveMethodInterceptor(
                FakeSessionInfoProvider(sessionInfo),
                FakeSessionControlManager(),
            ),
            VerificationMessageInterceptor(FakeSessionVerificationManager()),
        )
    }

    @Test
    suspend fun `LspBridge sendToLspServer with Message serializes and forwards`(@TempDir workspace: Path) {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, workspace)

        val message = LspMessages.publishDiagnostics(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspServer(message)

        val sent = serverConnector.sentToServer.receive()
        assertThat(sent).isEqualTo(message.serialize())
    }

    @Test
    suspend fun `LspBridge sendToLspServer with raw string forwards verbatim`(@TempDir workspace: Path) {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, workspace)

        proxy.sendToLspServer("raw-payload")

        assertThat(serverConnector.sentToServer.receive()).isEqualTo("raw-payload")
    }

    @Test
    suspend fun `LspBridge sendToLspClient with Message serializes and forwards`(@TempDir workspace: Path) {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, workspace)

        val message = LspMessages.publishDiagnostics(uri = "file:///workspace/a.oxsts")
        proxy.sendToLspClient(message)

        assertThat(clientConnector.sentToClient.receive()).isEqualTo(message.serialize())
    }

    @Test
    suspend fun `LspBridge sendToLspClient with raw string forwards verbatim`(@TempDir workspace: Path) {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, workspace)

        proxy.sendToLspClient("raw-response")

        assertThat(clientConnector.sentToClient.receive()).isEqualTo("raw-response")
    }

    @Test
    fun `LspBridge recordError increments error count surfaced by getInfo`(@TempDir workspace: Path) {
        val clientConnector = FakeLspClientRawConnector()
        val serverConnector = FakeLspServerRawConnector()
        val proxy = newProxy(clientConnector, serverConnector, workspace)

        assertThat(proxy.getInfo().errorCount).isEqualTo(0L)
        proxy.recordError()
        proxy.recordError()
        assertThat(proxy.getInfo().errorCount).isEqualTo(2L)
    }
}
