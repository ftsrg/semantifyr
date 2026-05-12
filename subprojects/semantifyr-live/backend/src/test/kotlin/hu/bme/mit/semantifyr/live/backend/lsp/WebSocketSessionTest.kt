/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Duration.Companion.seconds


class WebSocketSessionTest {

    private val adminPassword = "test-admin-password"

    private val modelWithCase = """
        package test

        @VerificationCase
        class CaseA {
            prop { return true }
        }
    """.trimIndent()

    private fun config(tmp: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(adminPassword = adminPassword, wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 16),
        )
    }

    private fun adminAuthHeader(): String {
        return "Basic " + Base64.getEncoder().encodeToString("admin:$adminPassword".toByteArray())
    }

    private suspend fun DefaultClientWebSocketSession.openWith(uri: String, text: String = modelWithCase) {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri = uri, languageId = "oxsts", text = text)))
    }

    private suspend fun DefaultClientWebSocketSession.discover(id: Int, uri: String): JsonArray {
        send(
            Frame.Text(
                LspWire.executeCommandRequest(
                    id = id,
                    command = "oxsts.case.discover",
                    arguments = listOf(JsonPrimitive(uri)),
                ),
            ),
        )
        return awaitResponseFor(id = id)["result"]?.jsonArray ?: error("discover returned no result")
    }

    @Test
    fun `initialize over websocket returns server capabilities`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val response = awaitResponseFor(id = 1)
            assertThat(response["result"]?.jsonObject?.get("capabilities")?.jsonObject).isNotNull
        }
    }

    @Test
    fun `session info request is intercepted and returns metadata`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(LspWire.request(id = 2, method = SemantifyrLiveMethods.SESSION_INFO)))
            val info = awaitResponseFor(id = 2)["result"]?.jsonObject ?: error("no session info")
            assertThat(info["flavorId"]?.jsonPrimitive?.contentOrNull).isEqualTo("oxsts")
            assertThat(info["workingDirectory"]?.jsonPrimitive?.contentOrNull).contains(tmp.toString())
        }
    }

    @Test
    fun `admin status reports the live session while connected`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        val client = jsonClient(webSockets = true)
        client.webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)

            val status = client.get("/api/admin/status") {
                header(HttpHeaders.Authorization, adminAuthHeader())
            }.body<AdminStatusResponse>()
            assertThat(status.sessions).hasSize(1)
            assertThat(status.sessions[0].flavorId).isEqualTo("oxsts")
        }
    }

    @Test
    fun `discover over websocket returns the verification cases`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            val cases = discover(id = 2, uri = "file:///workspace/snippet.oxsts")
            assertThat(cases).isNotEmpty
            assertThat(cases.map { it.jsonObject["label"]?.jsonPrimitive?.contentOrNull })
                .anyMatch { it != null && it.contains("CaseA") }
        }
    }

    @Test
    fun `semanticTokens full returns a non-empty data array`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            send(Frame.Text(LspWire.semanticTokensFullRequest(id = 2, uri = "file:///workspace/snippet.oxsts")))
            val data = awaitResponseFor(id = 2)["result"]?.jsonObject?.get("data")?.jsonArray
                ?: error("semanticTokens/full returned no data")
            assertThat(data).isNotEmpty
        }
    }

    @Test
    fun `rapid didChange does not break a later discover`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            repeat(20) { edit ->
                send(
                    Frame.Text(
                        LspWire.didChangeNotification(
                            uri = "file:///workspace/snippet.oxsts",
                            version = edit + 2,
                            text = "$modelWithCase\n// edit $edit",
                        ),
                    ),
                )
                delay(10)
            }
            assertThat(discover(id = 99, uri = "file:///workspace/snippet.oxsts")).isNotEmpty
        }
    }

    @Test
    fun `concurrent websocket sessions each discover independently`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        val client = jsonClient(webSockets = true)
        coroutineScope {
            val results = (1..4).map { session ->
                async {
                    client.webSocket("/ws/lsp/oxsts") {
                        val uri = "file:///workspace/s$session.oxsts"
                        openWith(uri)
                        delay(20L * session)
                        assertThat(discover(id = 2, uri = uri)).isNotEmpty
                    }
                }
            }
            withTimeout(60.seconds) {
                results.awaitAll()
            }
        }
    }

    @Test
    fun `a session past the global limit is closed with code 4429`(@TempDir tmp: Path) = testApplication {
        val limitedConfig = config(tmp).run { copy(sessionManager = sessionManager.copy(maxSessionsGlobal = 1)) }
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(limitedConfig)) }
        val client = jsonClient(webSockets = true)
        val firstReady = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        coroutineScope {
            val first = launch {
                client.webSocket("/ws/lsp/oxsts") {
                    send(Frame.Text(LspWire.initializeRequest()))
                    awaitResponseFor(id = 1)
                    firstReady.complete(Unit)
                    releaseFirst.await()
                }
            }
            withTimeout(30.seconds) {
                firstReady.await()
                client.webSocket("/ws/lsp/oxsts") {
                    val reason = closeReason.await()
                    assertThat(reason?.code).isEqualTo(4429.toShort())
                }
            }
            releaseFirst.complete(Unit)
            first.join()
        }
    }

    @Test
    fun `the per-session working directory is created during a session and removed afterwards`(
        @TempDir tmp: Path,
    ) = testApplication {
        installSemantifyrApp(webSockets = true) { installSemantifyrLiveBackend(testInjector(config(tmp))) }
        val sessionsDir = tmp.resolve("sessions")
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            assertThat(Files.list(sessionsDir).use { it.count() }).isEqualTo(1L)
        }
        withTimeout(5.seconds) {
            while (Files.isDirectory(sessionsDir) && Files.list(sessionsDir).use { it.count() } > 0L) {
                delay(20)
            }
        }
    }
}
