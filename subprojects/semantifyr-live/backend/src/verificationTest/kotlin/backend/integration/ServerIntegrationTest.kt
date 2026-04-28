/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import com.google.inject.Guice
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.server.AdminConfigResponse
import hu.bme.mit.semantifyr.live.backend.server.AdminHandler
import hu.bme.mit.semantifyr.live.backend.server.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.server.ApiRoutesHandler
import hu.bme.mit.semantifyr.live.backend.server.FlavorsResponse
import hu.bme.mit.semantifyr.live.backend.server.HealthResponse
import hu.bme.mit.semantifyr.live.backend.server.WebSocketHandler
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * End-to-end integration test: boots the full backend (Ktor + Guice + interceptor chain +
 * real [LspServerRunner][hu.bme.mit.semantifyr.live.backend.session.LspServerRawRunner])
 * and drives it via HTTP and WebSocket clients.
 *
 * Requires the LSP binaries to be staged. Tagged `slow` so it only runs under `endToEndTest`.
 */
// @Disable // @Tag("slow")
class ServerIntegrationTest {

    private val lspBinariesDirectory: Path? = System.getProperty("semantifyr.live.lsp")?.let { Path.of(it) }

    private val adminPassword = "integration-admin-password"

    private fun config(tmpRoot: Path): BackendConfig {
        val lspDir = checkNotNull(lspBinariesDirectory) {
            "System property 'semantifyr.live.lsp' must point to the staged LSP binaries directory"
        }
        return BackendConfig(
            server = ServerConfig(adminPassword = adminPassword),
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = tmpRoot.toString(),
                lspBinariesDirectory = lspDir.toString(),
                maxSessionsGlobal = 4,
                maxSessionsPerIp = 4,
            ),
        )
    }

    private fun adminAuthHeader(): String {
        val creds = Base64.getEncoder().encodeToString("admin:$adminPassword".toByteArray())
        return "Basic $creds"
    }

    private fun jsonClient(builder: ApplicationTestBuilder) = builder.createClient {
        install(ClientContentNegotiation) { json() }
        install(ClientWebSockets)
    }

    private fun ApplicationTestBuilder.configureServer(config: BackendConfig) {
        val injector = Guice.createInjector(BackendModule(config))
        install(
            createApplicationPlugin("integration-server") {
                application.install(ContentNegotiation) { json() }
                application.install(ServerWebSockets)
                with(injector.getInstance(ApiRoutesHandler::class.java)) { application.configure() }
                with(injector.getInstance(AdminHandler::class.java)) { application.configure() }
                with(injector.getInstance(WebSocketHandler::class.java)) { application.configure() }
            },
        )
    }

    @Test
    fun `health endpoint reports OK`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))

        val response = jsonClient(this).get("/api/health")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<HealthResponse>().status).isEqualTo("ok")
    }

    @Test
    fun `flavors endpoint lists configured flavors`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))

        val flavors = jsonClient(this).get("/api/flavors").body<FlavorsResponse>()
        assertThat(flavors.flavors).extracting("id").contains("oxsts", "xsts", "gamma")
    }

    @Test
    fun `admin status is reachable with valid credentials`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))

        val response = jsonClient(this).get("/api/admin/status") {
            header(HttpHeaders.Authorization, adminAuthHeader())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<AdminStatusResponse>().sessions).isEmpty()
    }

    @Test
    fun `admin config endpoint echoes configuration`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))

        val response = jsonClient(this).get("/api/admin/config") {
            header(HttpHeaders.Authorization, adminAuthHeader())
        }
        val adminConfig = response.body<AdminConfigResponse>()

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(adminConfig.maxSessionsGlobal).isEqualTo(4)
        assertThat(adminConfig.maxSessionsPerIp).isEqualTo(4)
    }

    @Test
    fun `websocket LSP session initializes a real LSP server`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))
        val client = jsonClient(this)

        client.clientWebSocket("/ws/lsp/oxsts") {
            val initRequest = lspRequest(
                id = 1,
                method = "initialize",
                params = buildJsonObject {
                    put("processId", JsonPrimitive(null as String?))
                    put("rootUri", JsonPrimitive("file:///workspace/"))
                    put("capabilities", buildJsonObject { })
                },
            )
            send(Frame.Text(initRequest))

            val response = awaitResponseFor(id = 1)
            assertThat(response["result"]?.jsonObject).isNotNull
        }
    }

    @Test
    fun `session info command is intercepted and returns session metadata`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))
        val client = jsonClient(this)

        client.clientWebSocket("/ws/lsp/oxsts") {
            send(Frame.Text(initializeRequest()))
            awaitResponseFor(id = 1)

            val sessionInfoRequest = lspRequest(
                id = 2,
                method = "workspace/executeCommand",
                params = buildJsonObject {
                    put("command", JsonPrimitive("semantifyr.session.info"))
                    put("arguments", kotlinx.serialization.json.JsonArray(emptyList()))
                },
            )
            send(Frame.Text(sessionInfoRequest))

            val response = awaitResponseFor(id = 2)
            val sessionInfo = response["result"]?.jsonObject
            assertThat(sessionInfo).isNotNull
            assertThat(sessionInfo!!["flavorId"]?.jsonPrimitive?.contentOrNull).isEqualTo("oxsts")
            assertThat(sessionInfo["started"]?.jsonPrimitive?.contentOrNull).isEqualTo("true")
        }
    }

    @Test
    fun `admin status reports the live session while connected`(@org.junit.jupiter.api.io.TempDir tmp: Path) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        configureServer(config(tmp))
        val client = jsonClient(this)

        client.clientWebSocket("/ws/lsp/oxsts") {
            send(Frame.Text(initializeRequest()))
            awaitResponseFor(id = 1)

            val status = client.get("/api/admin/status") {
                header(HttpHeaders.Authorization, adminAuthHeader())
            }.body<AdminStatusResponse>()

            assertThat(status.sessions).hasSize(1)
            assertThat(status.sessions[0].flavorId).isEqualTo("oxsts")
            assertThat(status.sessions[0].started).isTrue
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 1, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun `oxsts verification case passes end-to-end`(@org.junit.jupiter.api.io.TempDir tmp: Path) = runBlocking {
        // Boot a real embedded server on an ephemeral port and drive it via a real HTTP/WS client.
        // The verification itself is bounded by the @Timeout above so a misbehaving LSP/theta doesn't hang forever.
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")

        val modelSource = checkNotNull(javaClass.getResource("/integration/trafficlight.oxsts")) {
            "trafficlight.oxsts example model not on the test classpath"
        }.readText()
        val documentUri = "file:///workspace/snippet.oxsts"

        val injector = Guice.createInjector(BackendModule(config(tmp)))
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) { json() }
            install(io.ktor.server.websocket.WebSockets)
            with(injector.getInstance(ApiRoutesHandler::class.java)) { configure() }
            with(injector.getInstance(AdminHandler::class.java)) { configure() }
            with(injector.getInstance(WebSocketHandler::class.java)) { configure() }
        }.start(wait = false)

        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient(CIO) {
                install(ClientContentNegotiation) { json() }
                install(ClientWebSockets)
            }

            client.clientWebSocket("ws://localhost:$port/ws/lsp/oxsts") {
                send(Frame.Text(initializeRequest()))
                awaitResponseFor(id = 1)
                // Per LSP spec, signal init completion so the server moves past the handshake.
                send(Frame.Text(lspNotification(method = "initialized", params = buildJsonObject { })))

                // Open the example model so the LSP sees its contents.
                send(
                    Frame.Text(
                        lspNotification(
                            method = "textDocument/didOpen",
                            params = buildJsonObject {
                                put(
                                    "textDocument",
                                    buildJsonObject {
                                        put("uri", JsonPrimitive(documentUri))
                                        put("languageId", JsonPrimitive("oxsts"))
                                        put("version", JsonPrimitive(1))
                                        put("text", JsonPrimitive(modelSource))
                                    },
                                )
                            },
                        ),
                    ),
                )

                // Discover the verification cases the model declares.
                send(
                    Frame.Text(
                        lspRequest(
                            id = 2,
                            method = "workspace/executeCommand",
                            params = buildJsonObject {
                                put("command", JsonPrimitive("oxsts.case.discover"))
                                put("arguments", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(documentUri))))
                            },
                        ),
                    ),
                )
                val discoverResponse = awaitResponseFor(id = 2)
                val cases = discoverResponse["result"]?.let { it as? kotlinx.serialization.json.JsonArray }
                assertThat(cases).isNotNull
                assertThat(cases!!).isNotEmpty
                val reachableCase = cases.map { it.jsonObject }
                    .firstOrNull { it["label"]?.jsonPrimitive?.contentOrNull?.endsWith("GreenColorIsReachable") == true }
                    ?: error("Expected 'GreenColorIsReachable' verification case in the discovered list: $cases")

                // Ask the server to verify it. This exercises the full verification pipeline:
                // interceptor -> permit gate -> LSP -> theta executor -> response -> interceptor -> client.
                send(
                    Frame.Text(
                        lspRequest(
                            id = 3,
                            method = "workspace/executeCommand",
                            params = buildJsonObject {
                                put("command", JsonPrimitive("oxsts.case.verify"))
                                put(
                                    "arguments",
                                    kotlinx.serialization.json.JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("uri", JsonPrimitive(documentUri))
                                                put("range", reachableCase["location"]?.jsonObject?.get("range") ?: error("no range"))
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                )

                val verifyResponse = awaitResponseFor(id = 3, timeout = 1.minutes)
                val resultStatus = verifyResponse["result"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
                assertThat(resultStatus).isEqualTo("passed")
            }
            client.close()
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
            injector.getInstance(hu.bme.mit.semantifyr.live.backend.session.SessionManager::class.java).close()
        }
    }

    // --- LSP message helpers ---

    private fun initializeRequest(): String = lspRequest(
        id = 1,
        method = "initialize",
        params = buildJsonObject {
            put("processId", JsonPrimitive(null as String?))
            put("rootUri", JsonPrimitive("file:///workspace/"))
            put(
                "capabilities",
                buildJsonObject {
                    // Declare executeCommand + workDoneProgress so Xtext registers its command list during init.
                    put(
                        "workspace",
                        buildJsonObject {
                            put(
                                "executeCommand",
                                buildJsonObject {
                                    put("dynamicRegistration", JsonPrimitive(true))
                                },
                            )
                        },
                    )
                    put(
                        "window",
                        buildJsonObject {
                            put("workDoneProgress", JsonPrimitive(true))
                        },
                    )
                },
            )
        },
    )

    private fun lspRequest(
        id: Int,
        method: String,
        params: JsonObject,
    ): String {
        val obj = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        return obj.toString()
    }

    private fun lspNotification(method: String, params: JsonObject): String {
        val obj = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        return obj.toString()
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.awaitResponseFor(
        id: Int,
        timeout: kotlin.time.Duration = 30.seconds,
    ): JsonObject {
        return withTimeout(timeout) {
            while (true) {
                val frame = incoming.receive() as? Frame.Text ?: continue
                val obj = Json.parseToJsonElement(frame.readText()).jsonObject
                if (obj["id"]?.jsonPrimitive?.int == id) {
                    return@withTimeout obj
                }
                // Server-initiated LSP request (has both `id` and `method`): LSP blocks until we answer.
                // Send back an empty success response so the server can proceed.
                val method = obj["method"]?.jsonPrimitive?.contentOrNull
                val incomingRequestId = obj["id"]
                if (method != null && incomingRequestId != null) {
                    val ack = buildJsonObject {
                        put("jsonrpc", JsonPrimitive("2.0"))
                        put("id", incomingRequestId)
                        put("result", kotlinx.serialization.json.JsonNull)
                    }
                    send(Frame.Text(ack.toString()))
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }
}
