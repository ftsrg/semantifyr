/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.lsp.LspFrameCodec
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

class LiveSessionTest {

    private val testFlavor = Flavor(
        id = "oxsts",
        displayName = "OxSTS",
        binaryRelativePath = Path.of("oxsts"),
        fileName = "snippet.oxsts",
        languageId = "oxsts",
        workspaceLayout = WorkspaceLayout.SingleFile,
        verifyCommand = "oxsts.case.verify",
    )

    private class FakeLspStreams {
        val lspOutput = PipedOutputStream()
        val lspInput = PipedInputStream()

        private val clientToLsp = PipedOutputStream(lspInput)
        private val lspToClient = PipedInputStream(lspOutput)

        val stdin: OutputStream get() = clientToLsp
        val stdout: InputStream get() = lspToClient

        fun close() {
            clientToLsp.close()
            lspToClient.close()
        }
    }

    private fun createLiveSession(
        tmpDir: Path,
        streams: FakeLspStreams,
        concurrency: Int = 4,
    ): LiveSession {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmpDir.toString()),
            verification = VerificationConfig(concurrency = concurrency, timeout = 5.minutes),
        )
        val gate = mock<VerificationGate>()

        val lspRunner = mock<LspServerRunner>()
        wheneverBlocking { lspRunner.runLspWith(any(), any(), any(), any()) }.thenAnswer { invocation ->
            val block = invocation.getArgument<suspend (OutputStream, InputStream) -> Unit>(3)
            kotlinx.coroutines.runBlocking {
                try {
                    block(streams.stdin, streams.stdout)
                } finally {
                    streams.close()
                }
            }
        }

        return LiveSession(
            flavor = testFlavor,
            sessionId = "test-session",
            config = config,
            lspRunner = lspRunner,
            verificationGate = gate,
        )
    }

    @Test
    fun `workspace directory and stub file are created`(@TempDir tmpDir: Path) = testApplication {
        val streams = FakeLspStreams()
        val session = createLiveSession(tmpDir, streams)

        installSession(session)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.clientWebSocket("/ws/test") {
            streams.lspOutput.close()
            val reason = closeReason.await()
            assertThat(reason?.message).contains("LSP process exited")
        }

        val workDir = tmpDir.resolve("sessions").resolve(session.sessionId)
        assertThat(workDir.resolve("snippet.oxsts")).exists()
    }

    @Test
    fun `client message is forwarded to LSP`(@TempDir tmpDir: Path) = testApplication {
        val streams = FakeLspStreams()
        val session = createLiveSession(tmpDir, streams)

        installSession(session)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.clientWebSocket("/ws/test") {
            val clientUri = "file:///workspace/snippet.oxsts"
            val message = """{"jsonrpc":"2.0","id":1,"method":"textDocument/hover","params":{"textDocument":{"uri":"$clientUri"}}}"""
            send(Frame.Text(message))

            val frame = LspFrameCodec.readFrame(streams.lspInput)
            assertThat(frame).isNotNull()
            assertThat(frame).doesNotContain(clientUri)
            assertThat(frame).contains("snippet.oxsts")

            streams.lspOutput.close()
            closeReason.await()
        }
    }

    @Test
    fun `server message is forwarded to client with URI rewriting`(@TempDir tmpDir: Path) = testApplication {
        val streams = FakeLspStreams()
        val session = createLiveSession(tmpDir, streams)

        installSession(session)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.clientWebSocket("/ws/test") {
            val serverUri = tmpDir.resolve("sessions").resolve(session.sessionId).resolve("snippet.oxsts").toUri().toString()
            val serverMessage = """{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":{"uri":"$serverUri","diagnostics":[]}}"""
            LspFrameCodec.writeFrame(streams.lspOutput, serverMessage)

            val frame = incoming.receive() as Frame.Text
            val received = frame.readText()

            val clientUri = "file:///workspace/snippet.oxsts"
            assertThat(received).contains(clientUri)
            assertThat(received).doesNotContain(serverUri)

            streams.lspOutput.close()
            closeReason.await()
        }
    }

    @Test
    fun `didOpen syncs file to disk`(@TempDir tmpDir: Path) = testApplication {
        val streams = FakeLspStreams()
        val session = createLiveSession(tmpDir, streams)

        installSession(session)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.clientWebSocket("/ws/test") {
            val clientUri = "file:///workspace/snippet.oxsts"
            val didOpen = """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$clientUri","languageId":"oxsts","version":1,"text":"package test"}}}"""
            send(Frame.Text(didOpen))

            kotlinx.coroutines.delay(100)

            val filePath = tmpDir.resolve("sessions").resolve(session.sessionId).resolve("snippet.oxsts")
            assertThat(filePath.readText()).isEqualTo("package test")

            streams.lspOutput.close()
            closeReason.await()
        }
    }

    @Test
    fun `close cleans up workspace directory`(@TempDir tmpDir: Path) = testApplication {
        val streams = FakeLspStreams()
        val session = createLiveSession(tmpDir, streams)

        installSession(session)

        val wsClient = createClient { install(ClientWebSockets) }
        wsClient.clientWebSocket("/ws/test") {
            streams.lspOutput.close()
            closeReason.await()
        }

        session.close()
        val workDir = tmpDir.resolve("sessions").resolve(session.sessionId)
        assertThat(workDir).doesNotExist()
    }

    private fun ApplicationTestBuilder.installSession(session: LiveSession) {
        install(createApplicationPlugin("test-ws") {
            application.install(ServerWebSockets)
            application.routing {
                webSocket("/ws/test") {
                    session.run(this)
                }
            }
        })
    }
}
