/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
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

class WebSocketSyntaxErrorTest {

    private val uri = "file:///workspace/snippet.oxsts"

    private val validModel = """
        package test

        @VerificationCase
        class CaseA {
            prop {
                return true
            }
        }
    """.trimIndent()

    private val brokenModel = """
        package test

        @VerificationCase
        class CaseA {
            prop {
                return true
        }
    """.trimIndent()

    private val garbledModel = """
        package test

        } class ;; @@@ {
    """.trimIndent()

    private fun config(temporaryDirectory: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = temporaryDirectory.toString(), maxSessionsGlobal = 16),
        )
    }

    private suspend fun DefaultClientWebSocketSession.openValidModel(): VerificationCaseSpecification {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri, "oxsts", validModel)))
        send(Frame.Text(LspWire.executeCommandRequest(id = 2, command = "oxsts.case.discover", arguments = listOf(uri))))
        return awaitResponseFor(id = 2).resultAs(Array<VerificationCaseSpecification>::class.java).single()
    }

    @Test
    fun `discovery on a document with a syntax error answers and keeps the session open`(@TempDir temporaryDirectory: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(temporaryDirectory)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openValidModel()
            send(Frame.Text(LspWire.didChangeNotification(uri, 2, garbledModel)))
            send(Frame.Text(LspWire.executeCommandRequest(id = 3, command = "oxsts.case.discover", arguments = listOf(uri))))
            assertThat(awaitResponseFor(id = 3).resultAs(Array<VerificationCaseSpecification>::class.java)).isEmpty()
            send(Frame.Text(LspWire.didChangeNotification(uri, 3, validModel)))
            send(Frame.Text(LspWire.executeCommandRequest(id = 4, command = "oxsts.case.discover", arguments = listOf(uri))))
            assertThat(awaitResponseFor(id = 4).resultAs(Array<VerificationCaseSpecification>::class.java)).hasSize(1)
        }
    }

    @Test
    fun `verify on a document with a syntax error answers errored with a message and keeps the session open`(@TempDir temporaryDirectory: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(temporaryDirectory)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            val case = openValidModel()
            send(Frame.Text(LspWire.didChangeNotification(uri, 2, brokenModel)))
            val request = VerificationCaseRequest(uri, case.location().range, "smart")
            send(Frame.Text(LspWire.executeCommandRequest(id = 3, command = "oxsts.case.verify", arguments = listOf(request))))
            val result = awaitResponseFor(id = 3).resultAs(VerificationCaseResult::class.java)
            assertThat(result.status()).isEqualTo("errored")
            assertThat(result.message()).isNotBlank()
            send(Frame.Text(LspWire.executeCommandRequest(id = 4, command = "oxsts.case.discover", arguments = listOf(uri))))
            awaitResponseFor(id = 4)
        }
    }
}
