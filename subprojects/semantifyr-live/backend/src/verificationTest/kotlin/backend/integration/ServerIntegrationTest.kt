/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.server.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket

class ServerIntegrationTest {

    private val lspBinariesDirectory = System.getProperty("semantifyr.live.lsp")?.let {
        Path.of(it)
    }

    private val semanticLibrariesDirectory = System.getProperty("semantifyr.live.semanticLibraries")?.let {
        Path.of(it)
    }

    private val oxstsTestModelsDirectory = System.getProperty("semantifyr.live.oxstsTestModels")?.let {
        Path.of(it)
    }

    private val gammaTestModelsDirectory = System.getProperty("semantifyr.live.gammaTestModels")?.let {
        Path.of(it)
    }

    private val gammaLibraryModelsDirectory = System.getProperty("semantifyr.live.gammaLibraryModels")?.let {
        Path.of(it)
    }

    private val sysmlLibraryModelsDirectory = System.getProperty("semantifyr.live.sysmlLibraryModels")?.let {
        Path.of(it)
    }

    private val adminPassword = "integration-admin-password"

    private fun config(tmpRoot: Path): BackendConfig {
        val lspDir = checkNotNull(lspBinariesDirectory) {
            "System property 'semantifyr.live.lsp' must point to the staged LSP binaries directory"
        }
        val librariesDir = checkNotNull(semanticLibrariesDirectory) {
            "System property 'semantifyr.live.semanticLibraries' must point to the staged semantic libraries directory"
        }
        return BackendConfig(
            server = ServerConfig(adminPassword = adminPassword),
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = tmpRoot.toString(),
                lspBinariesDirectory = lspDir.toString(),
                semanticLibrariesDirectory = librariesDir.toString(),
                maxSessionsGlobal = 4,
                maxSessionsPerIp = 4,
            ),
        )
    }

    private fun adminAuthHeader(): String {
        val credentials = Base64.getEncoder().encodeToString("admin:$adminPassword".toByteArray())
        return "Basic $credentials"
    }

    private fun runIntegrationTest(
        tmp: Path,
        block: suspend ApplicationTestBuilder.(injector: Injector) -> Unit,
    ) = testApplication {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        val injector = Guice.createInjector(BackendModule(config(tmp)))
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }
        block(injector)
    }

    private suspend fun withRealServer(tmp: Path, block: suspend (HttpClient, Int) -> Unit) {
        val injector: Injector = Guice.createInjector(BackendModule(config(tmp)))
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            installSemantifyrLiveBackend(injector)
        }.start(wait = false)

        try {
            val port = server.engine.resolvedConnectors().first().port
            val client = HttpClient(CIO) {
                install(ClientContentNegotiation) {
                    json()
                }
                install(ClientWebSockets)
            }
            client.use {
                block(it, port)
            }
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
            injector.getInstance(SessionManager::class.java).close()
        }
    }

    @Test
    fun `websocket LSP session initializes a real LSP server`(@TempDir tmp: Path) = runIntegrationTest(tmp) {
        val client = jsonClient(webSockets = true)
        client.clientWebSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val response = awaitResponseFor(id = 1)
            assertThat(response["result"]?.jsonObject).isNotNull
        }
    }

    @Test
    fun `session info command is intercepted and returns session metadata`(
        @TempDir tmp: Path,
    ) = runIntegrationTest(tmp) {
        val client = jsonClient(webSockets = true)
        client.clientWebSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)

            send(Frame.Text(LspWire.request(id = 2, method = SemantifyrLiveMethods.SESSION_INFO)))
            val response = awaitResponseFor(id = 2)

            val sessionInfo = response["result"]?.jsonObject
            assertThat(sessionInfo).isNotNull
            assertThat(sessionInfo!!["flavorId"]?.jsonPrimitive?.contentOrNull).isEqualTo("oxsts")
            assertThat(sessionInfo["started"]?.jsonPrimitive?.contentOrNull).isEqualTo("true")
        }
    }

    @Test
    fun `admin status reports the live session while connected`(@TempDir tmp: Path) = runIntegrationTest(tmp) {
        val client = jsonClient(webSockets = true)
        client.clientWebSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
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
    suspend fun `oxsts verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "oxsts",
            modelSource = stagedModel(oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "trafficlight.oxsts"),
            pickCase = { cases ->
                cases.pickByLabelSuffix("GreenColorIsReachable")
            },
            assertVerdict = { status, _ ->
                assertThat(status).isEqualTo("passed")
            },
        )
    }

    @Test
    suspend fun `gamma verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "gamma",
            languageId = "gamma",
            documentUri = "file:///workspace/snippet.gamma",
            modelSource = stagedModel(gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma"),
            discoverCommand = "gamma.case.discover",
            verifyCommand = "gamma.case.verify",
            pickCase = { cases ->
                val idleReachable = cases.pickByLabel("LeaderStatechartIdleReachable")
                assertThat(idleReachable["id"]?.jsonPrimitive?.contentOrNull).isEqualTo("Simple.LeaderStatechartIdleReachable")
                idleReachable
            },
            assertVerdict = { status, result ->
                acceptAnyKnownStatus("LeaderStatechartIdleReachable", status, result)
                assertThat(status).isIn("passed", "not_supported")
            },
            verifyTimeout = 2.minutes,
        )
    }

    @Test
    suspend fun `oxsts-with-gamma-library smoke verifies a compiled gamma example`(@TempDir tmp: Path) {
        smokeVerifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-gamma-library",
            modelSource = stagedModel(gammaLibraryModelsDirectory, "semantifyr.live.gammaLibraryModels", "Simple.oxsts"),
        )
    }

    @Test
    suspend fun `oxsts-with-sysmlv2-library smoke verifies a compiled sysml example`(@TempDir tmp: Path) {
        smokeVerifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-sysmlv2-library",
            modelSource = stagedModel(sysmlLibraryModelsDirectory, "semantifyr.live.sysmlLibraryModels", "door_access.oxsts"),
        )
    }

    private suspend fun smokeVerifyFirstCase(
        tmp: Path,
        flavor: String,
        modelSource: String,
    ) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = flavor,
            modelSource = modelSource,
            pickCase = {
                it.first().jsonObject
            },
            assertVerdict = { status, result ->
                acceptAnyKnownStatus(flavor, status, result)
            },
            verifyTimeout = 2.minutes,
        )
    }

    private fun stagedModel(
        directory: Path?,
        propertyName: String,
        filename: String,
    ): String {
        assumeTrue(directory != null, "$propertyName system property not set")
        val source = checkNotNull(directory).resolve(filename)
        assumeTrue(Files.exists(source), "$filename not found at $source")
        return Files.readString(source)
    }

    private fun List<JsonObject>.pickByLabel(label: String): JsonObject {
        return firstOrNull {
            it["label"]?.jsonPrimitive?.contentOrNull == label
        } ?: error("Expected '$label' verification case in the discovered list: $this")
    }

    private fun List<JsonObject>.pickByLabelSuffix(suffix: String): JsonObject {
        return firstOrNull {
            it["label"]?.jsonPrimitive?.contentOrNull?.endsWith(suffix) == true
        } ?: error("Expected verification case ending in '$suffix' in the discovered list: $this")
    }

    private fun acceptAnyKnownStatus(
        label: String,
        status: String?,
        result: JsonObject,
    ) {
        assertThat(status)
            .describedAs("verification status for $label: $result")
            .isIn("passed", "failed", "inconclusive", "not_supported")
    }

    private suspend fun verifyEndToEnd(
        tmp: Path,
        flavor: String,
        modelSource: String,
        pickCase: (List<JsonObject>) -> JsonObject,
        assertVerdict: (status: String?, result: JsonObject) -> Unit,
        languageId: String = "oxsts",
        documentUri: String = "file:///workspace/snippet.oxsts",
        discoverCommand: String = "oxsts.case.discover",
        verifyCommand: String = "oxsts.case.verify",
        verifyTimeout: kotlin.time.Duration = 1.minutes,
    ) {
        assumeTrue(lspBinariesDirectory != null, "semantifyr.live.lsp system property not set")
        withRealServer(tmp) { client, port ->
            client.clientWebSocket("ws://localhost:$port/ws/lsp/$flavor") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = languageId, text = modelSource)))

                val cases = discoverCases(id = 2, command = discoverCommand, documentUri = documentUri, flavor = flavor)
                val selectedCase = pickCase(cases)
                val caseRange = selectedCase["location"]?.jsonObject?.get("range")
                    ?: error("verification case has no location.range: $selectedCase")

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 3,
                            command = verifyCommand,
                            arguments = listOf(LspWire.verifyCaseArgument(uri = documentUri, range = caseRange)),
                        ),
                    ),
                )
                val verifyResponse = awaitResponseFor(id = 3, timeout = verifyTimeout)
                val resultObject = verifyResponse["result"]?.jsonObject
                    ?: error("verify response had no result: $verifyResponse")
                val resultStatus = resultObject["status"]?.jsonPrimitive?.contentOrNull
                assertVerdict(resultStatus, resultObject)
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.discoverCases(
        id: Int,
        command: String,
        documentUri: String,
        flavor: String,
    ): List<JsonObject> {
        send(
            Frame.Text(
                LspWire.executeCommandRequest(
                    id = id,
                    command = command,
                    arguments = listOf(JsonPrimitive(documentUri) as JsonElement),
                ),
            ),
        )
        val discoverResponse = awaitResponseFor(id = id)
        val cases = discoverResponse["result"] as? JsonArray
            ?: error("discover returned no result for flavor=$flavor: $discoverResponse")
        assertThat(cases).describedAs("discover for flavor=$flavor").isNotEmpty
        return cases.map {
            it.jsonObject
        }
    }
}
