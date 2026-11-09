/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentParams
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentResult
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseCollectingNotifications
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.NavigateToParams
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebSocketNavigationTest {

    private val modelUri = "file:///workspace/snippet.oxsts"

    private val builtinPrefix = "file:///workspace/.semantifyr/builtin/"

    private val model = """
        package example::lamps

        class Lamp {
            var on: bool := false

            tran switchOn() {
                on := true
            }
        }

        class DimmableLamp : Lamp {
            redefine tran {
                inline switchOn()
            }
        }
    """.trimIndent()

    private fun config(tmp: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 16),
        )
    }

    private fun positionOf(needle: String): Position {
        val lines = model.lines()
        val line = lines.indexOfFirst {
            it.contains(needle)
        }
        return Position(line, lines[line].indexOf(needle))
    }

    private suspend fun DefaultClientWebSocketSession.openModel(): InitializeResult {
        send(Frame.Text(LspWire.initializeRequest()))
        val initializeResult = awaitResponseFor(id = 1).resultAs(InitializeResult::class.java)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri = modelUri, languageId = "oxsts", text = model)))
        return initializeResult
    }

    private suspend fun DefaultClientWebSocketSession.readDocument(id: Int, uri: String): String? {
        send(Frame.Text(LspWire.request(id = id, method = SemantifyrLiveMethods.READ_DOCUMENT, params = ReadDocumentParams(uri))))
        return awaitResponseFor(id = id).resultAs(ReadDocumentResult::class.java).text
    }

    private suspend fun DefaultClientWebSocketSession.workspaceSymbols(id: Int, query: String): List<WorkspaceSymbol> {
        send(Frame.Text(LspWire.request(id = id, method = "workspace/symbol", params = WorkspaceSymbolParams(query))))
        return awaitResponseFor(id = id).resultAs(Array<WorkspaceSymbol>::class.java).toList()
    }

    @Test
    fun `workspace symbols find members and the builtin library under client uris`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            val initializeResult = openModel()
            assertThat(initializeResult.capabilities.workspaceSymbolProvider.left).isTrue

            val switchOn = workspaceSymbols(id = 2, query = "switchOn").single()
            assertThat(switchOn.kind).isEqualTo(SymbolKind.Method)
            assertThat(switchOn.containerName).isEqualTo("example::lamps::Lamp")
            assertThat(switchOn.location.left.uri).isEqualTo(modelUri)
            assertThat(switchOn.location.left.range.start).isEqualTo(positionOf("switchOn() {"))
            val anything = workspaceSymbols(id = 3, query = "Anything").single {
                it.name == "Anything"
            }
            assertThat(anything.location.left.uri).startsWith(builtinPrefix)
            assertThat(readDocument(id = 4, uri = anything.location.left.uri)).contains("class Anything")
        }
    }

    @Test
    fun `code lens offers only advertised commands and goes to the redefined builtin transition`(
        @TempDir tmp: Path,
    ) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            val initializeResult = openModel()
            val advertisedCommands = initializeResult.capabilities.executeCommandProvider.commands
            assertThat(initializeResult.capabilities.codeLensProvider).isNotNull

            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/codeLens", params = CodeLensParams(TextDocumentIdentifier(modelUri)))))
            val lenses = awaitResponseFor(id = 2).resultAs(Array<CodeLens>::class.java).toList()

            val lensCommands = lenses.map {
                it.command.command
            }
            assertThat(lensCommands).isNotEmpty.isSubsetOf(advertisedCommands)
            val redefinedLens = lenses.single {
                it.command.command == "oxsts.redefinable.redefined.navigate" && it.range.start.line == positionOf("redefine tran").line
            }
            send(Frame.Text(LspWire.executeCommandRequest(id = 3, command = redefinedLens.command.command, arguments = redefinedLens.command.arguments)))
            val (_, notifications) = awaitResponseCollectingNotifications(id = 3, method = "workspace/navigateTo")
            val navigation = notifications.single().paramsAs(NavigateToParams::class.java)
            val target = navigation.locations().single()
            assertThat(target.uri).startsWith(builtinPrefix)
            assertThat(readDocument(id = 4, uri = target.uri)).contains("class Anything")
        }
    }

    @Test
    fun `definition into the builtin library returns a uri the client can read`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openModel()

            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/definition", params = DefinitionParams(TextDocumentIdentifier(modelUri), positionOf("bool")))))
            val definition = awaitResponseFor(id = 2).resultAs(Array<Location>::class.java).single()

            assertThat(definition.uri).startsWith(builtinPrefix)
            val builtinText = readDocument(id = 3, uri = definition.uri)
            assertThat(builtinText).isNotNull
            assertThat(builtinText!!.lines()[definition.range.start.line]).contains("extern datatype bool")
        }
    }
}
