/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebSocketWriteOrderTest {

    private fun config(temporaryDirectory: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = temporaryDirectory.toString(), maxSessionsGlobal = 16),
        )
    }

    private fun modelWithCase(name: String): String {
        return """
            package $name

            @VerificationCase
            class Case$name {
                prop {
                    return true
                }
            }
        """.trimIndent()
    }

    private suspend fun DefaultClientWebSocketSession.initialize() {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
    }

    private suspend fun DefaultClientWebSocketSession.caseLabels(id: Int, uri: String): List<String> {
        send(Frame.Text(LspWire.executeCommandRequest(id = id, command = "oxsts.case.discover", arguments = listOf(uri))))
        return awaitResponseFor(id = id).resultAs(Array<VerificationCaseSpecification>::class.java).map {
            it.label()
        }
    }

    @Test
    fun `documents opened back to back are all open`(@TempDir temporaryDirectory: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(temporaryDirectory)))
        }
        val names = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta")
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            initialize()
            for (name in names) {
                send(Frame.Text(LspWire.didOpenNotification("file:///workspace/$name.oxsts", "oxsts", modelWithCase(name))))
            }
            for ((index, name) in names.withIndex()) {
                assertThat(caseLabels(index + 10, "file:///workspace/$name.oxsts")).containsExactly("$name::Case$name")
            }
        }
    }

    @Test
    fun `a document opened and edited back to back ends in its latest text`(@TempDir temporaryDirectory: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(temporaryDirectory)))
        }
        val uri = "file:///workspace/snippet.oxsts"
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            initialize()
            send(Frame.Text(LspWire.didOpenNotification(uri, "oxsts", modelWithCase("first"))))
            send(Frame.Text(LspWire.didChangeNotification(uri, 2, modelWithCase("second"))))
            send(Frame.Text(LspWire.didChangeNotification(uri, 3, modelWithCase("third"))))
            assertThat(caseLabels(10, uri)).containsExactly("third::Casethird")
        }
    }
}
