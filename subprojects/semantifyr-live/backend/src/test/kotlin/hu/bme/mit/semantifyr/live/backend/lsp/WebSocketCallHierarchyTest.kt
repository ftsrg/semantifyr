/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebSocketCallHierarchyTest {

    private val lampsUri = "file:///workspace/Lamps.oxsts"

    private val lamps = """
        package example::library

        class Lamp {
            var on: bool := false

            tran toggle() {
                self.on := true
            }
        }
    """.trimIndent()

    private val garageUri = "file:///workspace/snippet.oxsts"

    private val garage = """
        package example::garage

        import example::library

        class Garage {
            contains lamp: Lamp[1]

            redefine tran {
                inline lamp.toggle()
            }
        }
    """.trimIndent()

    @Test
    fun `call hierarchy across documents answers with client uris`(@TempDir tmp: Path) = testApplication {
        val config = BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 16),
        )
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val capabilities = awaitResponseFor(id = 1).resultAs(InitializeResult::class.java).capabilities
            assertThat(capabilities.callHierarchyProvider.left).isTrue
            send(Frame.Text(LspWire.initializedNotification()))
            send(Frame.Text(LspWire.didOpenNotification(uri = lampsUri, languageId = "oxsts", text = lamps)))
            // A didOpen still queued is superseded by the next write, so the library must be open first.
            awaitPublishDiagnostics(lampsUri)
            send(Frame.Text(LspWire.didOpenNotification(uri = garageUri, languageId = "oxsts", text = garage)))

            val call = Range(Position(8, 20), Position(8, 26))
            val prepareParams = CallHierarchyPrepareParams(TextDocumentIdentifier(garageUri), call.start)
            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/prepareCallHierarchy", params = prepareParams)))
            val toggle = awaitResponseFor(id = 2).resultAs(Array<CallHierarchyItem>::class.java).single()
            assertThat(toggle.uri).isEqualTo(lampsUri)
            assertThat(toggle.selectionRange).isEqualTo(Range(Position(5, 9), Position(5, 15)))

            val incomingParams = CallHierarchyIncomingCallsParams(toggle)
            send(Frame.Text(LspWire.request(id = 3, method = "callHierarchy/incomingCalls", params = incomingParams)))
            val incoming = awaitResponseFor(id = 3).resultAs(Array<CallHierarchyIncomingCall>::class.java).single()
            assertThat(incoming.from.uri).isEqualTo(garageUri)
            assertThat(incoming.from.name).isEqualTo("main")
            assertThat(incoming.fromRanges).containsExactly(call)

            val outgoingParams = CallHierarchyOutgoingCallsParams(incoming.from)
            send(Frame.Text(LspWire.request(id = 4, method = "callHierarchy/outgoingCalls", params = outgoingParams)))
            val outgoing = awaitResponseFor(id = 4).resultAs(Array<CallHierarchyOutgoingCall>::class.java).single()
            assertThat(outgoing.to.uri).isEqualTo(lampsUri)
            assertThat(outgoing.fromRanges).containsExactly(call)
        }
    }
}
