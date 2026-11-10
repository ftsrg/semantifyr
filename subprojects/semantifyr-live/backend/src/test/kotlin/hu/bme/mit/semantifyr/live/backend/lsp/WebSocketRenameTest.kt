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
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebSocketRenameTest {

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

        class GarageLamp : Lamp {
            redefine tran toggle() {
                self.on := false
            }
        }
    """.trimIndent()

    @Test
    fun `rename across documents answers with client uris`(@TempDir tmp: Path) = testApplication {
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
            assertThat(capabilities.renameProvider.right.prepareProvider).isTrue
            send(Frame.Text(LspWire.initializedNotification()))
            send(Frame.Text(LspWire.didOpenNotification(uri = lampsUri, languageId = "oxsts", text = lamps)))
            // A didOpen still queued is superseded by the next write, so the library must be open first.
            awaitPublishDiagnostics(lampsUri)
            send(Frame.Text(LspWire.didOpenNotification(uri = garageUri, languageId = "oxsts", text = garage)))

            val redefinition = Position(5, 18)
            val prepareParams = PrepareRenameParams(TextDocumentIdentifier(garageUri), redefinition)
            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/prepareRename", params = prepareParams)))
            val prepared = awaitResponseFor(id = 2).resultAs(Range::class.java)
            assertThat(prepared).isEqualTo(Range(redefinition, Position(5, 24)))

            val renameParams = RenameParams(TextDocumentIdentifier(garageUri), redefinition, "flip")
            send(Frame.Text(LspWire.request(id = 3, method = "textDocument/rename", params = renameParams)))
            val edit = awaitResponseFor(id = 3).resultAs(WorkspaceEdit::class.java)
            assertThat(edit.changes).containsOnlyKeys(lampsUri, garageUri)
            assertThat(edit.changes[garageUri]).containsExactly(
                TextEdit(Range(redefinition, Position(5, 24)), "flip"),
            )
            assertThat(edit.changes[lampsUri]).containsExactly(
                TextEdit(Range(Position(5, 9), Position(5, 15)), "flip"),
            )
        }
    }
}
