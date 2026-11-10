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
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class InlineHintCodeActionTest {

    private val uri = "file:///workspace/snippet.oxsts"

    private val model = """
        package test

        class Counter {
            var count: int := 0

            redefine tran {
                if (1 < 2) {
                    count := 1
                }
            }
        }
    """.trimIndent()

    @Test
    fun `a compile-time if is published as a hint at its keyword`(@TempDir temporaryDirectory: Path) = testApplication {
        installLiveBackend(temporaryDirectory)
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            val diagnostics = openIfHints()
            assertThat(diagnostics).hasSize(1)
            assertThat(diagnostics[0].severity).isEqualTo(DiagnosticSeverity.Hint)
            assertThat(diagnostics[0].range.start).isEqualTo(Position(6, 8))
            assertThat(diagnostics[0].range.end).isEqualTo(Position(6, 10))
        }
    }

    @Test
    fun `the code action of the hint makes the if inline`(@TempDir temporaryDirectory: Path) = testApplication {
        installLiveBackend(temporaryDirectory)
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            val diagnostics = openIfHints()
            assertThat(diagnostics).hasSize(1)
            val hint = diagnostics[0]
            assertThat(hint.code?.left).isEqualTo("OXSTS.COULD_BE_INLINE")
            val params = CodeActionParams(TextDocumentIdentifier(uri), hint.range, CodeActionContext(diagnostics))
            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/codeAction", params = params)))
            val codeActions = awaitResponseFor(id = 2).resultAs(Array<CodeAction>::class.java)
            assertThat(
                codeActions.map {
                    it.title
                },
            ).containsExactly("Make inline")
            val edits = codeActions[0].edit.changes
            assertThat(edits.keys).containsExactly(uri)
            assertThat(
                edits.getValue(uri).map {
                    it.newText
                },
            ).anyMatch {
                it.contains("inline if")
            }
        }
    }

    private fun ApplicationTestBuilder.installLiveBackend(temporaryDirectory: Path) {
        val config = BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = temporaryDirectory.toString(), maxSessionsGlobal = 16),
        )
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config))
        }
    }

    private suspend fun DefaultClientWebSocketSession.openIfHints(): List<Diagnostic> {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri = uri, languageId = "oxsts", text = model)))
        return awaitPublishDiagnostics(uri).paramsAs(PublishDiagnosticsParams::class.java).diagnostics.filter {
            it.range.start == Position(6, 8)
        }
    }
}
