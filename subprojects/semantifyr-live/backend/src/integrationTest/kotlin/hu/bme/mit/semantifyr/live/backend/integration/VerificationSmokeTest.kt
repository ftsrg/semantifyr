/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.live.backend.integration.IntegrationTestSupport.config
import hu.bme.mit.semantifyr.live.backend.integration.IntegrationTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class VerificationSmokeTest {

    @Test
    suspend fun `oxsts verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "oxsts",
            modelSource = stagedModel(IntegrationTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "trafficlight.oxsts"),
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
            modelSource = stagedModel(IntegrationTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma"),
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
            modelSource = stagedModel(IntegrationTestSupport.gammaLibraryModelsDirectory, "semantifyr.live.gammaLibraryModels", "Simple.oxsts"),
        )
    }

    @Test
    suspend fun `oxsts-with-sysmlv2-library smoke verifies a compiled sysml example`(@TempDir tmp: Path) {
        smokeVerifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-sysmlv2-library",
            modelSource = stagedModel(IntegrationTestSupport.sysmlLibraryModelsDirectory, "semantifyr.live.sysmlLibraryModels", "door_access.oxsts"),
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
        verifyTimeout: Duration = 1.minutes,
    ) {
        IntegrationTestSupport.assumeStaged()
        withRealServer(config(tmp)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/$flavor") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = languageId, text = modelSource)))

                val cases = discoverCases(id = 2, command = discoverCommand, documentUri = documentUri, flavor = flavor)
                val selectedCase = pickCase(cases)
                val caseRange = selectedCase["location"]?.jsonObject?.get("range")
                    ?: error("verification case has no location.range: $selectedCase")
                val caseLabel = selectedCase["label"]?.jsonPrimitive?.contentOrNull
                    ?: error("verification case has no label: $selectedCase")

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 3,
                            command = verifyCommand,
                            arguments = listOf(
                                LspWire.verifyCommandArgument(
                                    uri = documentUri,
                                    range = caseRange,
                                    portfolio = "smart-full",
                                    caseLabel = caseLabel,
                                ),
                            ),
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
